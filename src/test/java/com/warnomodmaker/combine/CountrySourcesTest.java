package com.warnomodmaker.combine;

import java.util.List;

public class CountrySourcesTest {
    private static String country(String key, String flag) {
        return "(\"" + key + "\", TUISpecificCountryInfos(Country=\"" + key + "\" FlagTexture=\"" + flag + "\")),";
    }
    private static String source(String... rows) {
        return "// Generated source\r\nunnamed TUISpecificCountriesInfos\r\n(\r\n CountriesInfos = MAP [\r\n"
            + String.join("\r\n", rows) + "\r\n ]\r\n)\r\n";
    }
    public static void main(String[] args) {
        String original = country("US","old"), changed = country("US","new");
        String high = country("UKR","high"), low = country("UKR","low"), duplicate = country("UKR","duplicate");
        String first = source(original, high, duplicate);
        String second = source(changed, low, country("SWE","swe"));
        String merged = CountrySources.merge(source(original),List.of(first,second));
        if (!merged.contains(changed) || !merged.contains(high) || !merged.contains(country("SWE","swe"))
            || merged.contains(original) || merged.contains(low) || merged.contains(duplicate))
            throw new AssertionError(merged);
        String reversed = CountrySources.merge(source(original),List.of(second,first));
        if (!reversed.contains(low) || reversed.contains(high)) throw new AssertionError(reversed);
    }
}
