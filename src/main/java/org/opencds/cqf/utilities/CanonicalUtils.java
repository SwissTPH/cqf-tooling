package org.opencds.cqf.utilities;

import org.hl7.fhir.r4.model.CanonicalType;

public class CanonicalUtils {

    public static String getId(CanonicalType canonical) {
        if (canonical.hasValue()) {
            String id = canonical.getValue();
            String temp = id.contains("/") ? id.substring(id.lastIndexOf("/") + 1) : id;
            return temp.split("\\|")[0];
        }

        throw new RuntimeException("CanonicalType must have a value for id extraction");
    }

    public static String getResourceName(CanonicalType canonical) {
        if (canonical.hasValue()) {
            String id = canonical.getValue();
            if (id.contains("/")) {
                id = id.replace(id.substring(id.lastIndexOf("/")), "");
                return id.contains("/") ? id.substring(id.lastIndexOf("/") + 1) : id;
            }
            return null;
        }

        throw new RuntimeException("CanonicalType must have a value for resource name extraction");
    }
}
