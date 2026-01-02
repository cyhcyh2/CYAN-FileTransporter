package me.cyh2.cyan.filetransporter.utils;

import java.util.HashMap;
import java.util.Map;

public class WebErrorUtils {
    public static Map<String, Object> generateErrorMessage(String errorMsg) {
        var re = new HashMap<String, Object>();
        re.put("response", "failed");
        re.put("msg", errorMsg);
        return re;
    }
}
