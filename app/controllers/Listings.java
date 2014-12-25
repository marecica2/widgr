package controllers;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Comment;
import models.Event;
import models.FileUpload;
import models.Listing;
import models.ListingFilter;
import models.Rating;
import models.User;
import play.mvc.Before;
import utils.NumberUtils;
import utils.RandomUtil;
import utils.StringUtils;
import dto.ListingDTO;

public class Listings extends BaseController
{
    @Before(unless = { "listing", "listingsRest" })
    static void checkAccess()
    {
        checkAuthorizedAccess();
    }

    public static void listing(String uuid, String action, String url, String type)
    {
        final Boolean isNew = request.params.get("new") != null ? true : false;
        final Boolean edit = action != null && action.equals("edit") ? true : false;

        final User user = getLoggedUser();
        final Listing listing = Listing.get(uuid);
        if ((!isNew && listing == null) || (listing != null && listing.deleted != null))
            notFound();

        final List<Listing> listings = listing != null ? Listing.getForUser(listing.user) : null;
        final Boolean isOwner = listing != null ? listing.user.equals(user) : false;
        final Boolean fromEvent = false;
        final String temp = RandomUtil.getUUID();
        final String commentTemp = RandomUtil.getUUID();
        final Map<String, String> errs = new HashMap<String, String>();

        if (edit && !isOwner && !isNew)
            forbidden();

        if (isNew)
        {
            params.put("temp", temp);
            params.put("charging", "free");
            params.put("type", "p2p");
            params.put("privacy", "private");
            params.flash();
            render(user, isOwner, edit, url, errs, type,
                    temp, commentTemp, fromEvent);
        } else if (edit)
        {
            params.put("video", listing.video);
            params.put("title", listing.title);
            params.put("category", listing.category);
            params.put("tags", listing.tags);
            params.put("currency", listing.currency);
            params.put("description", listing.description);
            params.put("charging", listing.charging);
            params.put("type", listing.type);
            params.put("privacy", listing.privacy);
            params.put("price", listing.price.toString());
            params.put("chargingTime", listing.chargingTime + "");
            params.put("color", listing.color);
            params.put("image", listing.imageUrl);
            if (listing.chatEnabled != null && listing.chatEnabled)
                params.put("chatEnabled", "true");
            if (listing.firstFree != null && listing.firstFree)
                params.put("firstFree", "true");
            if (listing.commentsEnabled != null && listing.commentsEnabled)
                params.put("commentsEnabled", "true");
            params.put("temp", temp);
            params.flash();

            render(user, isOwner, edit, listing, url, errs, type,
                    temp, commentTemp, fromEvent);
        } else
        {
            final String name = user != null ? user.getFullName() : null;
            final String room = listing != null ? listing.uuid : null;
            final String rmtp = getProperty(CONFIG_RMTP_PATH);
            final String socketIo = getProperty(CONFIG_SOCKET_IO);

            final List<Comment> comments = Comment.getByListing(listing);

            final List<Rating> ratings = listing != null ? Rating.getByObject(uuid) : null;
            final Map<String, Object> stats = listing != null ? Rating.calculateStats(ratings) : null;

            render(user, isOwner, edit, listing, url, errs, type,
                    temp, commentTemp, comments, ratings, stats, fromEvent, listings, rmtp, socketIo, room, name);
        }
    }

    public static void listingPost(
        String uuid,
        String action,
        String title,
        String description,
        String privacy,
        String charging,
        String chargingTime,
        String type,
        String category,
        String tags,
        String price,
        String currency,
        String color,
        String url,
        String offset,
        String image,
        String imageId,
        String imageTempUrl,
        String video,
        String imageUrl,
        String chatEnabled,
        String commentsEnabled,
        String firstFree,
        String temp
        )
    {
        Listing listing = Listing.get(uuid);
        boolean edit = action != null && action.equals("edit") ? true : false;
        final User user = getLoggedUser();

        validation.required(type);
        validation.required(privacy);
        validation.required(charging);
        validation.required(title);
        validation.required(category);
        validation.required(description);

        if (Event.EVENT_CHARGING_BEFORE.equals(charging))
        {
            validation.required(price);
            validation.required(currency);
            validation.equals(charging, charging).message("validation.charging");
        }
        validation.required(title);
        validation.required(description);

        if (!validation.hasErrors())
        {
            if (listing == null)
            {
                listing = new Listing();
                listing.uuid = temp;
                listing.roomSecret = RandomUtil.getUUID();
                listing.created = new Date();
                listing.user = user;
                listing.imageUrl = FileuploadController.PATH_TO_LISTING_AVATARS + "ava_" + RandomUtil.getRandomInteger(22) + ".png";
                listing.state = Event.EVENT_STATE_USER_CREATED;
            }
            listing.chatEnabled = chatEnabled != null ? true : null;
            listing.firstFree = firstFree != null ? true : null;
            listing.commentsEnabled = commentsEnabled != null ? true : null;
            listing.title = title;
            listing.description = StringUtils.htmlEscape(description);
            listing.charging = charging;
            listing.price = NumberUtils.parseDecimal(price);
            listing.currency = currency;
            listing.color = color;
            listing.chargingTime = NumberUtils.parseInt(chargingTime);
            listing.type = type;
            listing.privacy = privacy;
            listing.category = category;
            listing.tags = tags;
            listing.video = video;
            if (imageUrl != null)
            {
                listing.imageUrl = imageUrl;
                listing.imageId = imageId;
            }
            listing.chatEnabled = chatEnabled != null ? true : null;
            listing.lastModified = new Date();
            listing.save();

            // mark file upload as stored
            if (imageId != null)
            {
                List<FileUpload> fu = FileUpload.getByObject(listing.uuid);
                for (FileUpload fileUpload : fu)
                {
                    fileUpload.stored = true;
                    fileUpload.save();
                }
            }
            redirectTo(url);
        }
        params.flash();
        render("Listings/listing.html", user, edit, listing);
    }

    public static void deleteListing(String uuid)
    {
        final User user = getLoggedUser();
        final Listing listing = Listing.get(uuid);

        // permissions check
        if (!user.isOwner(listing))
            forbidden();

        listing.deleted = true;
        listing.save();
        redirectTo("/");
    }

    public static void start(String id, String url)
    {
        final User user = getLoggedUser();
        final Listing l = Listing.get(id);

        if (user == null)
            forbidden();
        if (!l.user.equals(user))
            forbidden();

        l.started = new Date();
        l.ended = null;
        l.save();

        redirectTo(url);
    }

    public static void privateRoom(String id)
    {
        final User user = getLoggedUser();
        final Listing l = Listing.get(id);

        if (user == null)
            forbidden();
        if (!l.user.equals(user))
            forbidden();

        Event e = new Event();
        e.listing = l;
        e.listing_uuid = l.uuid;
        e.uuid = RandomUtil.getUUID();
        e.roomSecret = RandomUtil.getUUID();
        e.eventStart = new Date();
        e.eventEnd = new Date(e.eventStart.getTime() + (1000 * 60 * l.chargingTime));

        e.charging = l.charging;
        e.price = l.price;
        e.currency = l.currency;
        e.chargingTime = l.chargingTime;
        e.started = new Date();

        e.createdByUser = true;
        e.state = Event.EVENT_STATE_USER_CREATED;
        e.chatEnabled = true;
        e.commentsEnabled = false;
        e.privacy = Event.EVENT_VISIBILITY_PUBLIC;
        e.type = l.type;
        e.created = new Date();
        e.user = l.user;
        e.save();

        l.started = new Date();
        l.ended = null;
        l.instantBroadcast = e.uuid;
        l.save();

        redirectTo("/event/" + e.uuid);
    }

    public static void stop(String id, String url)
    {
        final Listing e = Listing.get(id);
        e.started = null;
        e.ended = new Date();
        e.instantBroadcast = null;
        e.save();
        redirectTo(url);
    }

    public static void resetImage(String uuid, String url)
    {
        final User user = getLoggedUser();
        final Listing listing = Listing.get(uuid);

        // permissions check
        if (!user.isOwner(listing))
            forbidden();

        listing.imageUrl = FileuploadController.PATH_TO_LISTING_AVATARS + "ava_" + RandomUtil.getRandomInteger(22) + ".png_thumb";
        listing.save();
        redirectTo(url);
    }

    public static void listingsRest()
    {
        final User user = getLoggedUser();
        final Integer first = request.params.get("first") != null ? Integer.parseInt(request.params.get("first")) : null;
        final Integer count = request.params.get("count") != null ? Integer.parseInt(request.params.get("count")) : null;

        final ListingFilter filterListing = new ListingFilter();
        filterListing.search = StringUtils.getStringOrNull(request.params.get("search"));
        filterListing.sort = StringUtils.getStringOrNull(request.params.get("sort"));
        filterListing.category = StringUtils.getStringOrNull(request.params.get("category"));

        List<ListingDTO> ListingsDto = new ArrayList<ListingDTO>();
        //List<Listing> listings = Listing.getFiltered(first, count, filterListing);
        List<Listing> listings = Listing.getSearch(first, count, filterListing);
        for (Listing l : listings)
        {
            ListingDTO lDto = ListingDTO.convert(l, user);
            ListingsDto.add(lDto);
        }
        renderJSON(ListingsDto);
    }

    public static void tagsRest(String query)
    {
        List<String> tags = Listing.getTags(query);
        renderJSON(tags);
    }
}