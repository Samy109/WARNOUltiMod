package com.warnomodmaker.combine;

import com.warnomodmaker.parser.NDFToken;
import com.warnomodmaker.parser.NDFTokenizer;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.*;
import static com.warnomodmaker.parser.NDFToken.TokenType.*;

/** Merge the country definitions and their two flag maps in the SDK's editable UI source. */
final class CountrySources {
    static final String PATH = "GameData\\Generated\\UserInterface\\UISpecificCountriesInfos.ndf";
    private static final Set<String> MAPS = Set.of("CountriesInfos", "Textures", "CountriesTextFormatScriptMap");
    private record Entry(String source, List<String> tokens) {}
    private record Section(int start, int end, Map<String,Entry> entries) {}

    static String merge(String vanilla, List<String> changed) {
        var base = sections(vanilla);
        var inputs = changed.stream().map(CountrySources::sections).toList();
        StringBuilder output = new StringBuilder(changed.getFirst());
        // Work backwards so offsets in the original source stay valid.
        var order = new ArrayList<>(inputs.getFirst().entrySet());
        order.sort(Comparator.comparingInt(e -> -e.getValue().start));
        for (var item : order) {
            String map = item.getKey();
            var section = item.getValue();
            Map<String,Entry> defaults = base.containsKey(map) ? base.get(map).entries : Map.of();
            Set<String> keys = new LinkedHashSet<>();
            for (var input : inputs) if (input.containsKey(map)) keys.addAll(input.get(map).entries.keySet());
            StringBuilder rows = new StringBuilder("\n");
            for (String key : keys) {
                Entry original = defaults.get(key), winner = original;
                for (var input : inputs) {
                    if (!input.containsKey(map)) continue;
                    Entry entry = input.get(map).entries.get(key);
                    if (entry == null ? original != null : original == null || !entry.tokens.equals(original.tokens)) {
                        winner = entry;
                        break;
                    }
                }
                if (winner != null) rows.append("        ").append(winner.source).append(",\n");
            }
            output.replace(section.start, section.end, rows.toString());
        }
        return output.toString();
    }

    private static Map<String,Section> sections(String source) {
        List<NDFToken> tokens;
        try {
            tokens = new NDFTokenizer(new StringReader(source),false).tokenize().stream()
                .filter(t -> t.getType() != COMMENT).toList();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
        List<Integer> lines = new ArrayList<>();
        lines.add(0);
        for (int i = 0; i < source.length(); i++) if (source.charAt(i) == '\n') lines.add(i + 1);
        Map<String,Section> result = new LinkedHashMap<>();
        for (int i = 0; i + 3 < tokens.size(); i++) {
            String name = tokens.get(i).getValue();
            if (!MAPS.contains(name) || tokens.get(i+2).getType() != MAP || tokens.get(i+3).getType() != OPEN_BRACKET) continue;
            int start = offset(tokens.get(i+3),lines) + 1;
            i += 4;
            Map<String,Entry> entries = new LinkedHashMap<>();
            while (tokens.get(i).getType() != CLOSE_BRACKET) {
                if (tokens.get(i).getType() == COMMA) { i++; continue; }
                if (tokens.get(i).getType() != OPEN_PAREN || tokens.get(i+1).getType() != STRING_LITERAL)
                    throw new CombineException("Expected a named entry in " + PATH + " (" + name + ").");
                int first = i, depth = 0;
                String key = tokens.get(i+1).getValue();
                do {
                    var type = tokens.get(i++).getType();
                    if (type == OPEN_PAREN) depth++;
                    if (type == CLOSE_PAREN) depth--;
                    if (type == EOF) throw new CombineException("Incomplete country entry " + key);
                } while (depth != 0);
                String row = source.substring(offset(tokens.get(first),lines), offset(tokens.get(i-1),lines)+1);
                entries.putIfAbsent(key,new Entry(row,tokens.subList(first,i).stream().map(NDFToken::getOriginalText).toList()));
            }
            result.put(name,new Section(start,offset(tokens.get(i),lines),entries));
        }
        return result;
    }
    private static int offset(NDFToken token,List<Integer> lines) {
        return lines.get(token.getLine()-1) + token.getColumn()-1;
    }
}
