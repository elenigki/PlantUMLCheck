package edu.UOI.plantumlcheck.controller.view;

import java.util.List;

// simple data holder for what the user selected
public record SelectionSummary(
        List<String> plantumlNames,
        List<String> sourceNames,
        boolean codeOnly
) {}
