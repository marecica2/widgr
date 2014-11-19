package controllers;

import java.net.URLEncoder;
import java.util.Date;
import java.util.Map;

import models.Account;
import models.Attendance;
import models.Event;
import models.User;
import payments.Paypal;
import payments.Paypal.AccessToken;
import payments.Paypal.DoExpressCheckoutResponse;
import play.Logger;
import play.Play;
import play.cache.Cache;
import play.libs.Crypto;
import play.mvc.Controller;
import play.mvc.Http.Header;
import utils.UriUtils;

public class BaseController extends Controller
{
    public static final String CONFIG_SERVER_IP = "star.configuration.ip";
    public static final String CONFIG_SERVER_DOMAIN = "star.configuration.domain";
    public static final String CONFIG_RMTP_PATH = "star.configuration.rmtp";
    public static final String CONFIG_STREAM_NAME = "star.configuration.stream";
    public static final String CONFIG_BASE_URL = "star.configuration.baseurl";

    public static final String CONFIG_PAYPAL_PROVIDER_ACCOUNT = "star.configuration.paypal.provider.account";
    public static final String CONFIG_PAYPAL_PROVIDER_ACCOUNT_MICROPAYMENT = "star.configuration.paypal.provider.account.micropayment";
    public static final String CONFIG_PAYPAL_ENDPOINT = "star.configuration.paypal.endpoint";
    public static final String CONFIG_PAYPAL_URL = "star.configuration.paypal.payment.url";
    public static final String CONFIG_PAYPAL_USER = "star.configuration.paypal.user";
    public static final String CONFIG_PAYPAL_PWD = "star.configuration.paypal.pwd";
    public static final String CONFIG_PAYPAL_SIGNATURE = "star.configuration.paypal.signature";
    public static final String CONFIG_PAYPAL_PERCENTAGE = "star.configuration.paypal.percentage";

    public static void redirectTo(String url)
    {
        redirect(UriUtils.redirectStr(url));
    }

    public static User getLoggedUser()
    {
        final String userLogin = getUserLogin();
        User u = (User) Cache.get(userLogin);
        if (u != null)
        {
            return u;
        } else
        {
            u = User.getUserByLogin(userLogin);
            Cache.set(userLogin, u);
        }
        return u;
    }

    public static boolean clearUserFromCache()
    {
        final String userLogin = getUserLogin();
        Cache.delete(userLogin);
        return true;
    }

    public static User getLoggedUserNotCache()
    {
        final String userLogin = getUserLogin();
        User u = User.getUserByLogin(userLogin);
        return u;
    }

    public static Account getAccountByUser()
    {
        User user = getLoggedUser();
        return user.account;
    }

    public static Account getAccountByEvent(String eventUuid)
    {
        Event event = Event.get(eventUuid);
        return event.account;
    }

    private static String getUserLogin()
    {
        return Secure.Security.connected();
    }

    public static String getProperty(String key)
    {
        return Play.configuration.getProperty(key);
    }

    public static String getIP()
    {
        return getProperty(CONFIG_SERVER_IP);
    }

    public static boolean isProd()
    {
        return Play.mode.isProd();
    }

    public static boolean isPublicRequest(Map<String, Header> headers)
    {
        final String referer = getReferrerUrl(headers);
        boolean publicRequest = false;
        if (referer.indexOf("/public/") != -1)
        {
            publicRequest = true;
        }
        return publicRequest;
    }

    public static boolean isPublicRequest()
    {
        final String referer = getReferrerUrl(request.headers);
        boolean publicRequest = false;
        if (referer.indexOf("/public/") != -1)
        {
            publicRequest = true;
        }
        return publicRequest;
    }

    public static boolean isUserLogged()
    {
        return Secure.Security.isConnected();
    }

    private static String getReferrerUrl(Map<String, Header> headers)
    {
        final String referer = headers.get("referer") != null ? headers.get("referer").value() : "";
        return referer;
    }

    public static String getReferrerParameter(Map<String, Header> headers, String param)
    {
        final String referer = getReferrerUrl(headers);
        return UriUtils.getSimpleParameter(referer, param);
    }

    static void checkAuthorizedAccess()
    {
        if (Secure.Security.isConnected() || getReferrerUrl(request.headers).contains("/public/calendar"))
        {
        } else
        {
            flash.put("url", request.url);
            Secure.login();
        }
    }

    protected static void checkPayPalPayment(Event e, String transactionId, String url) throws Throwable
    {
        if (e.getCharging().equals(Event.EVENT_CHARGING_FREE))
        {
            Logger.info("Free event, not needed to log in");
            return;
        }

        if (!e.getCharging().equals(Event.EVENT_CHARGING_FREE) && (!isUserLogged() || getLoggedUser() == null))
        {
            Logger.info("Redirect to login");
            flash.put("url", request.url);
            Secure.login();
        }

        if (!e.getCharging().equals(Event.EVENT_CHARGING_FREE) && isUserLogged() && getLoggedUser() != null)
        {
            final User user = getLoggedUser();
            Attendance attendance = e.getInviteForCustomer(user);

            // in case of public event and missing attendance create it for the logged customer programatically
            if (attendance == null && e.getPrivacy().equals(Event.EVENT_VISIBILITY_PUBLIC))
                attendance = createAttendanceForCustomerEvent(e, user);

            String cancelUrl = getProperty(BaseController.CONFIG_BASE_URL) + url.substring(1);
            String redirectUrl = getProperty(BaseController.CONFIG_BASE_URL) + "paypal/checkout/" + e.uuid + "?url=" + URLEncoder.encode(url, "UTF-8") + "&transactionId="
                    + Crypto.encryptAES(user.uuid);
            System.err.println(redirectUrl);

            final Paypal pp = new Paypal(
                    e,
                    redirectUrl,
                    cancelUrl,
                    getProperty(CONFIG_PAYPAL_PROVIDER_ACCOUNT),
                    getProperty(CONFIG_PAYPAL_PROVIDER_ACCOUNT_MICROPAYMENT),
                    getProperty(CONFIG_PAYPAL_USER),
                    getProperty(CONFIG_PAYPAL_PWD),
                    getProperty(CONFIG_PAYPAL_SIGNATURE),
                    getProperty(CONFIG_PAYPAL_ENDPOINT),
                    getProperty(CONFIG_PAYPAL_URL),
                    getProperty(CONFIG_PAYPAL_PERCENTAGE)
                    );

            // payment received
            if (transactionId != null && Crypto.decryptAES(transactionId).equals(user.uuid) && request.params.get("PayerID") != null)
            {
                attendance.payerId = request.params.get("PayerID");
                final DoExpressCheckoutResponse resp = pp.doExpressCheckoutDual(attendance.accessToken, attendance.payerId, e);

                if (resp.success)
                {
                    attendance.paid = true;
                    attendance.fee = resp.fee;
                    attendance.transactionDate = new Date();
                    attendance.price = resp.price;
                    attendance.providerPrice = resp.providerPrice;
                    attendance.currency = e.account.currency;
                    attendance.paypalAccount = e.account.paypalAccount;
                    attendance.paypalAccountProvider = resp.providerAccount;
                    attendance.transactionId = resp.transactionIdProvider;
                    attendance.transactionIdProvider = resp.transactionIdOur;
                    attendance.save();
                    redirectTo(url);
                } else
                {
                    Logger.error(resp.errorMessage);
                    flash.put("paypalError", resp.errorMessage);
                    redirectTo(cancelUrl);
                }
            }

            // proceed to payment
            if ((attendance.paid == null || attendance.paid != true))
            {
                if (request.method.equals("GET"))
                {
                    renderTemplate("PaypalController/payment.html", user, e, url);
                }

                if (request.method.equals("POST"))
                {
                    AccessToken token = pp.setExpressCheckoutDual(e);
                    attendance.accessToken = token.getToken();
                    attendance.accessTokenValidity = token.getValidity();
                    attendance.save();
                    redirect(pp.getPaymentUrl(token.getToken()));
                }
            } else
            {
                Logger.info("Payment is accepted, displaying room");
                return;
            }
        }

        response.status = 400;
        renderText("Bad request");
    }

    private static Attendance createAttendanceForCustomerEvent(Event e, User customer)
    {
        Attendance attendance;
        attendance = new Attendance();
        attendance.event = e;
        attendance.account = Account.getAccountByEvent(e.uuid);
        attendance.customer = customer;
        attendance.created = new Date();
        attendance.email = customer.login;
        attendance.isForUser = false;
        attendance.result = Attendance.ATTENDANCE_RESULT_ACCEPTED;
        attendance.save();
        return attendance;
    }

}