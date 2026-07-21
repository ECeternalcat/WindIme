package com.garaho.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compact embedded English word list for the {@link EnglishT9Engine}
 * (design doc §3.3.2 - English T9 predictive dictionary).
 *
 * <p>Words are T9-encoded at class-load time; lookups return exact and
 * prefix matches ranked shortest-first (the classic T9 preference for the
 * most likely short completion). Replace with a larger Trie for production
 * coverage; the lookup surface stays identical.
 */
public final class EnglishDictionary {

    private static final String[] WORDS = {
            "a", "an", "and", "any", "are", "area", "arm", "army", "art", "as", "at", "all",
            "about", "above", "after", "again", "against", "also", "able", "acid", "age",
            "ago", "air", "aim", "allow", "almost", "alone", "along", "already", "always",
            "be", "be", "bed", "been", "best", "better", "big", "black", "blue", "body",
            "book", "both", "boy", "but", "buy", "back", "bad", "ball", "band", "bank",
            "base", "basic", "battle", "beach", "bear", "beat", "because", "become", "before",
            "begin", "being", "believe", "below", "between", "beyond", "birth", "bit", "blood",
            "board", "boat", "born", "boss", "box", "boy", "branch", "break", "bright",
            "bring", "broad", "brother", "brown", "build", "burn", "business", "busy", "but",
            "by", "bye",
            "call", "can", "car", "card", "care", "carry", "case", "cash", "cat", "catch",
            "cause", "cell", "chain", "chair", "change", "charge", "cheap", "check", "child",
            "china", "choice", "choose", "city", "civil", "claim", "class", "clean", "clear",
            "click", "client", "climb", "clock", "close", "cloud", "club", "coach", "coast",
            "code", "coffee", "cold", "collect", "college", "color", "come", "common",
            "company", "compare", "complete", "computer", "condition", "confirm", "connect",
            "consider", "contact", "continue", "control", "cook", "cool", "copy", "corner",
            "correct", "cost", "could", "count", "country", "course", "court", "cover",
            "create", "cross", "cup", "current", "cut", "culture", "cup",
            "data", "dance", "danger", "dark", "date", "day", "dead", "deal", "dear",
            "death", "debate", "decade", "decide", "decision", "deep", "degree", "delay",
            "deliver", "demand", "design", "detail", "develop", "device", "dial", "die",
            "difference", "different", "difficult", "dinner", "direct", "director", "dirty",
            "discount", "discover", "discuss", "disease", "distance", "divide", "do", "doctor",
            "document", "does", "dog", "dollar", "domain", "done", "door", "double", "doubt",
            "down", "draw", "dream", "dress", "drink", "drive", "drop", "drug", "dry",
            "due", "during", "duty",
            "each", "ear", "early", "earth", "east", "easy", "eat", "economic", "economy",
            "edge", "edit", "education", "effect", "effort", "either", "election", "else",
            "email", "emergency", "emotion", "employ", "end", "energy", "engine", "english",
            "enjoy", "enough", "enter", "entire", "equal", "error", "escape", "especially",
            "establish", "evaluate", "even", "evening", "event", "ever", "every", "evidence",
            "exact", "example", "excellent", "except", "exchange", "excuse", "exercise",
            "exist", "expect", "experience", "experiment", "expert", "explain", "explore",
            "express", "extra", "eye",
            "face", "fact", "factor", "fail", "fair", "fall", "family", "famous", "far",
            "farm", "fast", "father", "fault", "fear", "feature", "federal", "feed", "feel",
            "few", "field", "fight", "figure", "file", "fill", "film", "final", "find",
            "fine", "finger", "finish", "fire", "firm", "first", "fish", "five", "fix",
            "flag", "flat", "flight", "floor", "flow", "flower", "fly", "focus", "follow",
            "food", "foot", "for", "force", "forget", "form", "former", "forward", "four",
            "free", "fresh", "friend", "from", "front", "fruit", "full", "fun", "function",
            "fund", "future",
            "game", "garden", "gas", "gate", "gather", "general", "get", "girl", "give",
            "glad", "glass", "global", "go", "goal", "god", "gold", "golden", "good",
            "great", "green", "ground", "group", "grow", "growth", "guess", "guest", "guide",
            "gun",
            "hair", "half", "hall", "hand", "handle", "hang", "happen", "happy", "hard",
            "hate", "have", "he", "head", "health", "hear", "heart", "heat", "heavy",
            "hello", "help", "her", "here", "hey", "high", "him", "history", "hit", "hold",
            "home", "hope", "horse", "hospital", "hot", "hotel", "hour", "house", "how",
            "huge", "human", "hundred", "husband",
            "ice", "idea", "identify", "if", "image", "imagine", "impact", "important",
            "improve", "include", "increase", "indeed", "indicate", "individual", "industry",
            "information", "inside", "install", "instead", "interest", "internet", "into",
            "introduce", "invest", "involve", "island", "issue", "it", "item",
            "job", "join", "just",
            "keep", "key", "kick", "kid", "kill", "kind", "king", "knee", "know", "knowledge",
            "lab", "labor", "land", "language", "large", "last", "late", "later", "laugh",
            "law", "lawyer", "lay", "lead", "leader", "learn", "least", "leave", "left",
            "legal", "less", "let", "letter", "level", "library", "life", "light", "like",
            "likely", "limit", "line", "link", "list", "listen", "little", "live", "local",
            "lock", "long", "look", "lose", "loss", "lot", "love", "low", "lower", "luck",
            "lunch",
            "machine", "made", "magazine", "mail", "main", "maintain", "major", "make",
            "male", "man", "manage", "many", "map", "march", "mark", "market", "master",
            "match", "matter", "may", "maybe", "me", "mean", "measure", "media", "medical",
            "meet", "meeting", "member", "memory", "men", "message", "method", "middle",
            "might", "mile", "military", "million", "mind", "mine", "minute", "miss",
            "mission", "model", "modern", "moment", "money", "month", "more", "morning",
            "most", "mother", "motion", "move", "movie", "much", "music", "must", "my",
            "myself",
            "name", "nation", "national", "natural", "nature", "near", "nearly", "necessary",
            "need", "network", "never", "new", "news", "next", "nice", "night", "nine",
            "no", "none", "nor", "normal", "north", "not", "note", "nothing", "notice",
            "now", "number", "nurse",
            "object", "obtain", "occur", "ocean", "of", "off", "offer", "office", "officer",
            "official", "often", "oh", "oil", "ok", "old", "once", "one", "only", "open",
            "operate", "opinion", "opportunity", "option", "or", "order", "organize",
            "original", "other", "our", "out", "outside", "over", "own",
            "page", "paint", "pair", "palace", "pan", "paper", "parent", "park", "part",
            "particular", "partner", "party", "pass", "past", "path", "patient", "pattern",
            "pay", "peace", "people", "per", "perform", "perhaps", "period", "person",
            "personal", "phone", "photo", "physical", "pick", "picture", "piece", "pin",
            "place", "plan", "plant", "play", "player", "please", "plus", "point", "police",
            "policy", "politics", "poor", "popular", "population", "position", "positive",
            "possible", "post", "power", "practice", "prepare", "present", "president",
            "press", "pretty", "prevent", "price", "print", "private", "prize", "probably",
            "problem", "process", "produce", "product", "program", "project", "promise",
            "proof", "proper", "protect", "prove", "provide", "public", "pull", "purpose",
            "push", "put",
            "quality", "question", "quick", "quiet", "quite",
            "race", "radio", "rain", "raise", "range", "rate", "rather", "reach", "read",
            "ready", "real", "reality", "realize", "really", "reason", "receive", "recent",
            "record", "red", "reduce", "reflect", "region", "relate", "relationship",
            "release", "remain", "remember", "remove", "repeat", "replace", "reply",
            "report", "represent", "require", "research", "resource", "respond", "response",
            "rest", "result", "return", "reveal", "rich", "ride", "right", "ring", "rise",
            "risk", "road", "rock", "role", "room", "round", "rule", "run", "rural",
            "safe", "safety", "same", "save", "say", "school", "science", "score", "sea",
            "search", "season", "seat", "second", "secret", "section", "secure", "see",
            "seek", "seem", "select", "sell", "send", "senior", "sense", "series", "serious",
            "serve", "service", "set", "settle", "seven", "several", "shake", "shall",
            "shape", "share", "she", "sheet", "shift", "shine", "ship", "shirt", "shoe",
            "shoot", "shop", "short", "should", "shout", "show", "shut", "sick", "side",
            "sign", "signal", "silent", "silver", "similar", "simple", "since", "sing",
            "single", "sir", "sister", "sit", "site", "six", "size", "skill", "skin",
            "sky", "sleep", "slow", "small", "smart", "smile", "smoke", "snow", "social",
            "society", "soft", "software", "soil", "soldier", "solution", "some", "son",
            "song", "soon", "sorry", "sort", "sound", "source", "south", "space", "speak",
            "special", "specific", "speech", "speed", "spend", "spirit", "sport", "spring",
            "square", "staff", "stage", "stand", "standard", "star", "start", "state",
            "station", "stay", "step", "still", "stock", "stop", "store", "storm", "story",
            "strategy", "street", "strength", "stress", "strict", "strike", "strong",
            "structure", "struggle", "student", "study", "stuff", "style", "subject",
            "succeed", "success", "such", "sudden", "suffer", "suggest", "summer", "sun",
            "support", "sure", "surface", "surprise", "survive", "system",
            "table", "take", "talk", "tall", "task", "tax", "tea", "teach", "team", "tell",
            "ten", "tend", "term", "test", "text", "than", "thank", "that", "the", "their",
            "them", "theme", "then", "theory", "there", "these", "they", "thing", "think",
            "third", "this", "those", "though", "thought", "thousand", "three", "through",
            "throw", "ticket", "tie", "time", "tiny", "tip", "tired", "title", "to", "today",
            "together", "tomorrow", "tone", "tonight", "too", "tool", "top", "total",
            "touch", "tough", "tour", "toward", "town", "track", "trade", "tradition",
            "traffic", "train", "travel", "treat", "tree", "trial", "trip", "troops",
            "trouble", "true", "trust", "truth", "try", "turn", "tv", "twelve", "twenty",
            "two", "type",
            "ugly", "under", "understand", "union", "unit", "unite", "universe", "unless",
            "until", "up", "upon", "urban", "us", "use", "used", "useful", "user", "usual",
            "value", "various", "very", "victim", "video", "view", "violence", "visit",
            "voice", "vote",
            "wait", "wake", "walk", "wall", "want", "war", "warm", "warn", "wash", "watch",
            "water", "way", "we", "weak", "wealth", "wear", "weather", "web", "week",
            "weight", "welcome", "well", "west", "western", "what", "when", "where",
            "whether", "which", "while", "white", "who", "whole", "whose", "why", "wide",
            "wife", "wild", "will", "win", "wind", "window", "wine", "wing", "winter",
            "wire", "wish", "with", "within", "without", "woman", "wonder", "wood", "word",
            "work", "worker", "world", "worry", "worse", "worth", "would", "write", "wrong",
            "yard", "yeah", "year", "yellow", "yes", "yet", "you", "young", "your", "youth",
            "zero", "zone", "zoo",
    };

    private static final Map<String, List<String>> EXACT;
    private static final List<WordEntry> INDEX;

    static final class WordEntry {
        final String code;
        final String word;

        WordEntry(String code, String word) {
            this.code = code;
            this.word = word;
        }
    }

    static {
        Map<String, LinkedHashSet<String>> exact = new HashMap<>();
        List<WordEntry> index = new ArrayList<>(WORDS.length);
        Set<String> seen = new HashSet<>();
        for (String raw : WORDS) {
            if (raw == null || raw.isEmpty() || !seen.add(raw)) {
                continue;
            }
            String code = EnglishT9Codec.encode(raw);
            LinkedHashSet<String> bucket = exact.get(code);
            if (bucket == null) {
                bucket = new LinkedHashSet<>();
                exact.put(code, bucket);
            }
            bucket.add(raw);
            index.add(new WordEntry(code, raw));
        }
        Map<String, List<String>> frozenExact = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : exact.entrySet()) {
            frozenExact.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        EXACT = Collections.unmodifiableMap(frozenExact);
        INDEX = Collections.unmodifiableList(index);
    }

    private EnglishDictionary() {
    }

    public static List<String> exactMatches(String code) {
        List<String> r = EXACT.get(code);
        return r == null ? Collections.<String>emptyList() : r;
    }

    /**
     * @return words whose T9 code equals or starts with {@code prefix},
     *         ranked exact-match first then by ascending length.
     */
    public static List<String> matches(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.addAll(exactMatches(prefix));
        List<WordEntry> prefixHits = new ArrayList<>();
        for (WordEntry e : INDEX) {
            if (!e.code.equals(prefix) && e.code.startsWith(prefix)) {
                prefixHits.add(e);
            }
        }
        Collections.sort(prefixHits, new Comparator<WordEntry>() {
            @Override
            public int compare(WordEntry a, WordEntry b) {
                int c = Integer.compare(a.word.length(), b.word.length());
                if (c != 0) {
                    return c;
                }
                return a.word.compareToIgnoreCase(b.word);
            }
        });
        for (WordEntry e : prefixHits) {
            out.add(e.word);
        }
        return new ArrayList<>(out);
    }

    public static int wordCount() {
        return INDEX.size();
    }
}
