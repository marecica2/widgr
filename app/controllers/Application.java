package controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import models.Comment;
import models.Contact;
import models.Event;
import models.Listing;
import models.Message;
import models.Rating;
import models.Search;
import models.User;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;

import play.cache.Cache;
import play.i18n.Messages;
import play.mvc.Before;
import utils.RandomUtil;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import email.EmailNotificationBuilder;

//@With(Secure.class)
public class Application extends BaseController
{
    @Before(only = { "dashboard" })
    static void checkAccess() throws Throwable
    {
        checkAuthorizedAccess();
    }

    public static void about()
    {
        final User user = getLoggedUser();
        String uuid = RandomUtil.getUUID();
        params.put("uuid", uuid);
        params.flash();
        renderTemplate("Application/about.html", user, uuid);
    }

    public static void contact(String id)
    {
        final User user = getLoggedUser();
        final String uuid = RandomUtil.getUUID();
        final User usr = id != null ? User.getUserByLogin(id) : null;
        final List<Contact> followers = usr != null ? Contact.getFollowers(usr) : null;
        final List<Contact> followees = usr != null ? Contact.getFollowing(usr) : null;
        final Contact follow = usr != null ? Contact.isFollowing(user, usr, followers) : null;
        params.put("uuid", uuid);
        params.flash();
        render(user, uuid, usr, followers, followees, follow);
    }

    public static void contactUs(String uuid, String name, String email, String subject, String message, String captcha, String id)
    {
        final User user = getLoggedUser();
        final Object cap = Cache.get("captcha." + uuid);

        if (user == null)
        {
            validation.required("email", email);
            validation.required("name", name);
            validation.email("email", email).message("validation.login");
            validation.required("captcha", captcha);
        }

        validation.required("subject", subject);
        validation.required("message", message);

        if (captcha != null && cap != null)
            validation.equals(captcha, cap).message("invalid.captcha");

        if (!validation.hasErrors())
        {
            flash.success(Messages.get("message-sent-successfully"));
            flash.keep();

            String body = "Sender: " + name + " (" + email + ")\n\n";
            body += message;

            User recipient = getAdminUser();
            if (id != null)
                recipient = User.getUserByLogin(id);

            // send notification
            Message.createNotification(user, recipient, subject, body);

            // send email
            if (recipient != null && recipient.emailNotification)
            {
                EmailNotificationBuilder eb = new EmailNotificationBuilder();
                eb.setTo(recipient);
                eb.setFrom(user)
                        .setSubject(Messages.getMessage(recipient.locale, "somebody-wrote-you"))
                        .setMessageWiki(body)
                        .send();
            }

            contact(id);
        } else
        {
            uuid = RandomUtil.getUUID();
            flash.error(Messages.get("message-sent-error"));
            params.put("uuid", uuid);
            params.flash();
            final User usr = id != null ? User.getUserByLogin(id) : null;
            renderTemplate("Application/contact.html", user, uuid, usr);
        }

    }

    public static void facebook() throws IOException
    {
        final User user = getLoggedUser();
        final String param = request.params.get("signed_request");
        String userId = null;
        String pageId = null;
        Boolean admin = false;

        if (param != null)
        {
            final String[] parts = param.split("\\.");
            final String part = StringUtils.newStringUtf8(Base64.decodeBase64(parts[1]));
            final JsonObject jo = new JsonParser().parse(part).getAsJsonObject();
            if (jo.get("user_id") != null)
                userId = jo.get("user_id").getAsString();
            if (jo.get("page") != null && jo.get("page").getAsJsonObject().get("id") != null)
                pageId = jo.get("page").getAsJsonObject().get("id").getAsString();
            if (jo.get("page") != null && jo.get("page").getAsJsonObject().get("admin") != null)
                admin = jo.get("page").getAsJsonObject().get("admin").getAsBoolean();

            // if owner opens tab
            if (user != null && userId != null && userId.equals(user.facebookId) && admin != null && admin)
            {
                final User usr = getLoggedUserNotCache();
                usr.facebookTab = pageId;
                usr.save();
                clearUserFromCache();
                session.put("facebookTab", true);
            }
        }

        if (admin && user == null)
            redirect("/login?url=" + request.url);

        if (pageId == null)
            pageId = session.get("pageId");

        User displayedUser = pageId != null ? User.getUserByFacebookPage(pageId) : null;
        if (displayedUser != null && displayedUser.facebookPageType != null)
        {
            if (displayedUser.facebookPageType.equals("calendar"))
                redirect("/user/" + displayedUser.login + "/calendar");
            if (displayedUser.facebookPageType.equals("profile"))
                redirect("/user/" + displayedUser.login);
            if (displayedUser.facebookPageType.equals("channel"))
                redirect("/channel/" + displayedUser.facebookPageChannel);
        }

        redirect("/dashboard");
    }

    public static void facebookPost(String id, String type, String channel) throws IOException
    {
        User user = User.getUserByUUID(id);
        user.facebookPageType = type;
        user.facebookPageChannel = channel;
        user.save();
        clearUserFromCache();
        facebook();
    }

    public static void privacy()
    {
        final User user = getLoggedUser();
        render(user);
    }

    public static void terms()
    {
        final User user = getLoggedUser();
        render(user);
    }

    public static void help()
    {
        final User user = getLoggedUser();
        render(user);
    }

    public static void home()
    {
        final User user = getLoggedUser();
        Map<String, Object> ratings = initRatings();
        render(user, ratings);
    }

    private static Map<String, Object> initRatings()
    {
        Map<String, Object> ratings = (Map<String, Object>) Cache.get("ratings");

        //TODO update this condition
        if (ratings == null || true)
        {

        }
        return ratings;
    }

    public static void search(String query)
    {
        List<String> result = Search.tags(query.toLowerCase());
        renderJSON(result);
    }

    public static void channels()
    {
        final User user = getLoggedUser();
        render(user);
    }

    public static void dashboard(String type, Integer results)
    {
        final User user = getLoggedUser();
        final Boolean isOwner = true;
        final Boolean dashboard = true;
        final String temp = RandomUtil.getUUID();
        final String commentTemp = RandomUtil.getUUID();
        final List<Event> watchList = user != null ? Event.getWatchList(user) : null;
        final List<Event> approved = user != null ? Event.getApprovement(user) : null;
        final List<Listing> listings = user != null ? Listing.getForUser(user) : null;
        final List<Contact> contacts = Contact.getContacts(user);
        final String baseUrl = getBaseUrlWithoutSlash();

        final List<Comment> comments = Comment.getByFollower(user, 0, 200);
        //Http.Cookie c = new Http.Cookie();
        //c.name = "timezone";
        //c.value = user.timezone.toString();
        //request.cookies.put("timezone", c);
        render(user, watchList, listings, approved, type, isOwner, contacts, comments, temp, commentTemp, results, dashboard, baseUrl);
    }

    public static void manageChannels()
    {
        final User user = getLoggedUser();
        final Boolean isOwner = true;
        final Boolean dashboard = true;
        final List<Contact> contacts = Contact.getContacts(user);
        final List<Listing> listings = user != null ? Listing.getForUser(user) : null;

        boolean displayMsg1 = false;
        if (!user.isPublisher())
            displayMsg1 = true;
        render(user, listings, isOwner, contacts, dashboard, displayMsg1);
    }

    public static void approvements()
    {
        final User user = getLoggedUser();
        final Boolean isOwner = true;
        final Boolean dashboard = true;
        final List<Event> approved = user != null ? Event.getApprovement(user) : null;
        final List<Contact> contacts = Contact.getContacts(user);

        render(user, approved, isOwner, contacts, dashboard);
    }

    public static void upcoming()
    {
        final Boolean dashboard = true;
        final User user = getLoggedUser();
        final Boolean isOwner = true;
        final List<Contact> contacts = Contact.getContacts(user);
        render(user, isOwner, contacts, dashboard);
    }

    public static void past()
    {
        final Boolean dashboard = true;
        final User user = getLoggedUser();
        final Boolean isOwner = true;
        final List<Contact> contacts = Contact.getContacts(user);
        render(user, isOwner, contacts, dashboard);
    }

    public static void userProfile(String userLogin)
    {
        final boolean userProfile = true;
        final User user = getLoggedUser();
        final User usr = User.getUserByLogin(userLogin);

        if (usr == null)
            notFound();

        final Boolean isOwner = user != null && usr != null && usr.equals(user) ? true : false;
        final Contact contact = user != null ? Contact.get(user, usr) : null;

        final List<Contact> followers = Contact.getFollowers(usr);
        final List<Contact> followees = Contact.getFollowing(usr);
        final Contact follow = Contact.isFollowing(user, usr, followers);

        final List<Rating> ratings = Rating.getByUser(usr.uuid);
        final List<Listing> listings = Listing.getForUser(usr);
        final Map<String, Object> stats = Rating.calculateStats(ratings);

        final String name = user != null ? user.getFullName() : Messages.get("anonymous") + RandomUtil.getRandomDigits(5);
        final String room = usr != null ? usr.uuid : null;
        final String rmtp = getProperty(CONFIG_RMTP_PATH);
        final String socketIo = getProperty(CONFIG_SOCKET_IO);

        render(user, usr, userProfile, isOwner, listings, followees,
                followers, follow, ratings, stats, contact, name, room, rmtp, socketIo);
    }

    public static void calendarUser(String login, String channel) throws Throwable
    {
        final User user = getLoggedUser();
        final User userDisplayed = User.getUserByLogin(login);
        final Boolean isOwner = user != null && user.equals(userDisplayed) ? true : false;
        final List<Contact> followers = Contact.getFollowers(userDisplayed);
        final List<Contact> followees = Contact.getFollowing(userDisplayed);
        final Contact follow = Contact.isFollowing(user, userDisplayed, followers);
        final Listing listing = channel != null ? Listing.get(channel) : null;
        final List<Listing> listings = user != null ? Listing.getForUser(user) : null;

        if (channel != null)
        {
            if (!userDisplayed.isPublisher())
                forbidden();
            if (user == null)
                redirectToLogin(request.url);
            flash.put("success", Messages.get("click-and-drag-to-create-event"));
        }
        renderTemplate("/Application/calendar.html", user, userDisplayed, isOwner, listing, listings, followers, followees, follow);
    }

}