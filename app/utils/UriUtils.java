package utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UriUtils
{
    public static String getSimpleParameter(String url, String param)
    {
        try
        {
            return getQueryParams(url).get(param).get(0);
        } catch (Exception e)
        {
            return null;
        }
    }

    public static String redirectStr(String url)
    {
        if (url == null)
        {
            url = "/";
        }
        try
        {
            url = URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        url = url.replaceAll("amp;", "");
        url = url.replaceAll("\r", "");
        url = url.replaceAll("\n", "");
        return url;
    }

    public static String urlEncode(String url)
    {
        if (url == null)
            return null;
        try
        {
            url = URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        return url;
    }

    public static String urlDecode(String url)
    {
        try
        {
            url = URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        return url;
    }

    public static Map<String, List<String>> getQueryParams(String url)
    {
        try
        {
            Map<String, List<String>> params = new HashMap<String, List<String>>();
            String[] urlParts = url.split("\\?");
            if (urlParts.length > 1)
            {
                String query = urlParts[1];
                for (String param : query.split("&"))
                {
                    String[] pair = param.split("=");
                    String key = URLDecoder.decode(pair[0], "UTF-8");
                    String value = "";
                    if (pair.length > 1)
                    {
                        value = URLDecoder.decode(pair[1], "UTF-8");
                    }

                    List<String> values = params.get(key);
                    if (values == null)
                    {
                        values = new ArrayList<String>();
                        params.put(key, values);
                    }
                    values.add(value);
                }
            }

            return params;
        } catch (UnsupportedEncodingException ex)
        {
            throw new AssertionError(ex);
        }
    }
}
