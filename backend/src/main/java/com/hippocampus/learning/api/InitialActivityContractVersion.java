package com.hippocampus.learning.api;

import org.springframework.util.MultiValueMap;

enum InitialActivityContractVersion {
    LEGACY, ONE, INVALID;
    static InitialActivityContractVersion parse(MultiValueMap<String,String> query) {
        var values=query.get("initialActivityContract");
        if(values==null) return LEGACY;
        return values.size()==1 && "1".equals(values.getFirst()) ? ONE : INVALID;
    }
}
