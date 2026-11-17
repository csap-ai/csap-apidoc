package ai.csap.apidoc.runtime.filter.model;

import java.util.ArrayList;
import java.util.List;

public class MatchPage {
    private List<Match> rows = new ArrayList<>(List.of(new Match()));
    private long total = 1;
    private int size = 20;

    public List<Match> getRows() {
        return rows;
    }

    public long getTotal() {
        return total;
    }

    public int getSize() {
        return size;
    }
}
