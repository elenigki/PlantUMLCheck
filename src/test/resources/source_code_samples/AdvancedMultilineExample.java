package test;

import java.util.*;

public class AdvancedMultilineExample {

    @Deprecated
    private
    List<
        Book
    > books
    = new ArrayList<>();

    /* This is a
       multiline comment */
    @Override
    public
    Map<
        String,
        List<Page>
    >
    getPageMap(
        String owner // this is a param
    ) {
        return null;
    }

    // Should not break parsing
    public
    void setBooks(
        List<Book> books
    ) {
        this.books = books;
    }
}
