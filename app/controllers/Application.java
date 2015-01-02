package controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import models.Comment;
import models.Contact;
import models.Event;
import models.Listing;
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

//@With(Secure.class)
public class Application extends BaseController
{
    @Before(unless = { "home", "channels", "calendarUser", "facebook" })
    static void checkAccess() throws Throwable
    {
        checkAuthorizedAccess();
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
            if (admin != null && admin)
            {
                session.put("pageId", pageId);
                session.put("admin", admin);
            }

            // if owner opens tab
            if (user != null && userId != null && userId.equals(user.facebookId))
            {
                System.err.println("is Admin");
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
        List<Listing> listings = null;
        if (displayedUser != null && admin)
            listings = Listing.getForUser(displayedUser);

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

    public static void home()
    {
        final User user = getLoggedUser();
        Map<String, Object> ratings = initRatings();

        if (user != null && !user.isPublisher())
            //Start helping others and become a publisher. <a href='/settings/request-publisher'>Request for publisher account here</a> or <a href='/help#publisher'>learn more</a>
            flash.success(Messages.get("start-helping-others"));
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

        final List<Comment> comments = Comment.getByFollower(user, 0, 200);

        if (user != null && !user.isPublisher())
            flash.success(Messages.get("start-helping-others"));

        //Http.Cookie c = new Http.Cookie();
        //c.name = "timezone";
        //c.value = user.timezone.toString();
        //request.cookies.put("timezone", c);
        render(user, watchList, listings, approved, type, isOwner, contacts, comments, temp, commentTemp, results, dashboard);
    }

    public static void calendarUser(String login, String channel) throws Throwable
    {
        final User user = getLoggedUser();
        final User userDisplayed = User.getUserByLogin(login);
        final Boolean isPublic = request.params.get("public") != null ? true : false;
        final Boolean isOwner = user != null && user.equals(userDisplayed) && !isPublic ? true : false;

        // for event request user must be logged in
        if (channel != null)
        {
            if (user == null)
                redirectToLogin(request.url);
            flash.put("success", Messages.get("click-and-drag-to-create-event"));
        }

        final List<Contact> followers = Contact.getFollowers(userDisplayed);
        final List<Contact> followees = Contact.getFollowing(userDisplayed);
        final Contact follow = Contact.isFollowing(user, userDisplayed, followees);
        final Listing listing = channel != null ? Listing.get(channel) : null;
        final List<Listing> listings = user != null ? Listing.getForUser(user) : null;
        renderTemplate("/Application/calendar.html", user, userDisplayed, isOwner, listing, listings, followers, followees, follow);
    }
}