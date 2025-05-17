package com.alchemyLab.general_chem_website.util;

import java.util.HashMap;
import java.util.Map;

public class ResponseUtil {
    public static Map<String, Object> createResponse(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("message", message);
        return map;
    }
}
