public class SetterAggregationExample {

    private Chapter mainChapter;
    private Book ignored;

    public void setMainChapter(Chapter chapter) {
        this.mainChapter = chapter;  // AGGREGATION
    }

    public void setIgnored(Book b) {
        // No assignment → no relationship
    }

    public void assignDifferent(Chapter c) {
        Chapter temp = c; // should NOT count
    }
}
