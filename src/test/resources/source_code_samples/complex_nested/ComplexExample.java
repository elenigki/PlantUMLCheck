public class ComplexExample {

    private Map<String, List<Book>> bookIndex; // ASSOCIATION
    private Set<Section> sections = new HashSet<>(); // COMPOSITION

    public void loadPages(List<Page> pages) { // DEPENDENCY
        // no-op
    }

    public Optional<Chapter> getChapter() { // DEPENDENCY
        return Optional.empty();
    }

    public Map<String, List<Page>> getPageMap() { // DEPENDENCY
        return null;
    }
}
