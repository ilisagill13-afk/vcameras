package com.visabot.canada;

import java.util.LinkedHashMap;
import java.util.Map;

public class Facilities {

    public static class Facility {
        public final int id;
        public final String name;
        public final String key;
        public Facility(String key, int id, String name) {
            this.key = key; this.id = id; this.name = name;
        }
        @Override public String toString() { return name; }
    }

    public static final Map<String, Facility> ALL = new LinkedHashMap<>();

    static {
        ALL.put("calgary",     new Facility("calgary",     89, "Calgary"));
        ALL.put("halifax",     new Facility("halifax",     90, "Halifax"));
        ALL.put("montreal",    new Facility("montreal",    91, "Montreal"));
        ALL.put("ottawa",      new Facility("ottawa",      92, "Ottawa"));
        ALL.put("quebec-city", new Facility("quebec-city", 93, "Quebec City"));
        ALL.put("toronto",     new Facility("toronto",     94, "Toronto"));
        ALL.put("vancouver",   new Facility("vancouver",   95, "Vancouver"));
    }

    public static final String[] VISA_TYPES = {"B1/B2", "F1", "J1", "H1B", "L1", "O1"};
    public static final String[] VISA_LABELS = {
        "B1/B2 — Business/Tourism",
        "F1 — Student",
        "J1 — Exchange Visitor",
        "H1B — Specialty Occupation",
        "L1 — Intracompany Transfer",
        "O1 — Extraordinary Ability",
    };
}
