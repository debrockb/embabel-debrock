package com.matoe.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.matoe.domain.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Renders an {@link UnforgettableItinerary} into a printable PDF using iText 7.
 *
 * <p>The output is a single-document A4 PDF with: cover page (destination,
 * dates, guests, total estimated cost), region/weather/currency summary,
 * accommodations table, transport table, day-by-day variant breakdowns, and
 * an attractions section.
 *
 * <p>Synthetic (LLM-generated) results are visually tagged in the source
 * column of each table so downstream consumers can distinguish bookable
 * inventory from AI estimates.
 */
@Service
public class PdfExportService {

    private static final DeviceRgb HEADING_COLOR = new DeviceRgb(30, 64, 175);
    private static final DeviceRgb MUTED_COLOR = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb WARNING_COLOR = new DeviceRgb(180, 83, 9);

    public byte[] render(UnforgettableItinerary itinerary) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            renderCover(doc, itinerary);
            renderInsights(doc, itinerary);
            renderAccommodations(doc, itinerary.accommodations());
            renderTransport(doc, itinerary.transport());
            renderVariants(doc, itinerary.variants());
            renderAttractions(doc, itinerary.attractions());
        } catch (Exception e) {
            throw new RuntimeException("PDF render failed: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    // ── sections ──────────────────────────────────────────────────────────────

    private void renderCover(Document doc, UnforgettableItinerary it) {
        doc.add(new Paragraph("M.A.T.O.E — Unforgettable Itinerary")
            .setFontSize(10).setFontColor(MUTED_COLOR));
        doc.add(new Paragraph(it.destination())
            .setFontSize(32).setFontColor(HEADING_COLOR).setBold());
        doc.add(new Paragraph(it.startDate() + " → " + it.endDate())
            .setFontSize(14).setFontColor(MUTED_COLOR));
        doc.add(new Paragraph(it.guestCount() + " guest(s) · est. total €" +
            String.format("%.0f", it.totalEstimatedCost())));
        if (it.createdAt() != null) {
            doc.add(new Paragraph("Generated " + it.createdAt())
                .setFontSize(9).setFontColor(MUTED_COLOR));
        }
        doc.add(new Paragraph("\n"));
    }

    private void renderInsights(Document doc, UnforgettableItinerary it) {
        heading(doc, "Destination Intelligence");
        appendMap(doc, "Regional insights", it.regionInsights());
        appendMap(doc, "Weather forecast", it.weatherForecast());
        appendMap(doc, "Currency", it.currencyInfo());
    }

    private void renderAccommodations(Document doc, List<AccommodationOption> list) {
        if (list == null || list.isEmpty()) return;
        heading(doc, "Accommodations");
        Table t = new Table(UnitValue.createPercentArray(new float[]{3, 2, 2, 1, 2, 1}))
            .useAllAvailableWidth();
        headerRow(t, "Name", "Location", "Type", "Tier", "Per night", "Source");
        for (AccommodationOption a : list) {
            t.addCell(cell(a.name()));
            t.addCell(cell(a.location()));
            t.addCell(cell(a.type()));
            t.addCell(cell(a.tier()));
            t.addCell(cell("€" + String.format("%.0f", a.pricePerNight())));
            t.addCell(sourceCell(a.source()));
        }
        doc.add(t);
    }

    private void renderTransport(Document doc, List<TransportOption> list) {
        if (list == null || list.isEmpty()) return;
        heading(doc, "Transport");
        Table t = new Table(UnitValue.createPercentArray(new float[]{2, 2, 3, 2, 1, 1}))
            .useAllAvailableWidth();
        headerRow(t, "Type", "Provider", "Origin → Destination", "Duration", "Price", "Source");
        for (TransportOption tr : list) {
            t.addCell(cell(tr.type()));
            t.addCell(cell(tr.provider()));
            t.addCell(cell((tr.origin() != null ? tr.origin() : "?") + " → "
                + (tr.destination() != null ? tr.destination() : "?")));
            t.addCell(cell(tr.duration()));
            t.addCell(cell("€" + String.format("%.0f", tr.price())));
            t.addCell(sourceCell(tr.source()));
        }
        doc.add(t);
    }

    private void renderVariants(Document doc, List<ItineraryVariant> variants) {
        if (variants == null || variants.isEmpty()) return;
        heading(doc, "3-Tier Variants");
        for (ItineraryVariant v : variants) {
            doc.add(new Paragraph(safe(v.tier()).toUpperCase() + " — €"
                + String.format("%.0f", v.totalEstimatedCost()))
                .setFontSize(14).setFontColor(HEADING_COLOR).setBold());
            if (v.highlights() != null && !v.highlights().isEmpty()) {
                doc.add(new Paragraph("Highlights: " + String.join(", ", v.highlights())));
            }
            if (v.tradeoffs() != null && !v.tradeoffs().isBlank()) {
                doc.add(new Paragraph("Trade-offs: " + v.tradeoffs()).setFontColor(MUTED_COLOR));
            }
            if (v.dayByDay() != null) {
                for (ItineraryDay d : v.dayByDay()) {
                    doc.add(new Paragraph("  Day " + d.dayNumber() + " — " + safe(d.date())
                        + (d.title() != null ? " · " + d.title() : "")).setBold());
                    if (d.summary() != null) line(doc, "    ", d.summary());
                    if (d.morningActivities() != null)
                        line(doc, "    Morning", String.join("; ", d.morningActivities()));
                    if (d.afternoonActivities() != null)
                        line(doc, "    Afternoon", String.join("; ", d.afternoonActivities()));
                    if (d.eveningActivities() != null)
                        line(doc, "    Evening", String.join("; ", d.eveningActivities()));
                    if (d.meals() != null && !d.meals().isEmpty())
                        line(doc, "    Meals", String.join(", ", d.meals()));
                    if (d.transportNotes() != null) line(doc, "    Transport", d.transportNotes());
                }
            }
            doc.add(new Paragraph("\n"));
        }
    }

    private void renderAttractions(Document doc, List<AttractionOption> list) {
        if (list == null || list.isEmpty()) return;
        heading(doc, "Attractions & Experiences");
        for (AttractionOption a : list) {
            doc.add(new Paragraph(safe(a.name())).setBold());
            doc.add(new Paragraph(safe(a.description())).setFontColor(MUTED_COLOR));
            doc.add(new Paragraph(String.format("  %s · €%.0f · %s · %s",
                safe(a.category()), a.price(), safe(a.duration()), safe(a.location())))
                .setFontSize(10));
            if ("llm".equalsIgnoreCase(a.source())) {
                doc.add(new Paragraph("  (AI-generated estimate — verify before booking)")
                    .setFontSize(9).setFontColor(WARNING_COLOR));
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void heading(Document doc, String text) {
        doc.add(new Paragraph(text).setFontSize(18).setFontColor(HEADING_COLOR).setBold());
    }

    private void headerRow(Table t, String... cols) {
        for (String c : cols) {
            t.addHeaderCell(new Cell().add(new Paragraph(c).setBold())
                .setBackgroundColor(new DeviceRgb(241, 245, 249)));
        }
    }

    private Cell cell(String s) {
        return new Cell().add(new Paragraph(safe(s)).setFontSize(10));
    }

    private Cell sourceCell(String source) {
        Cell c = new Cell().add(new Paragraph(safe(source)).setFontSize(9));
        if ("llm".equalsIgnoreCase(source)) {
            c.setFontColor(WARNING_COLOR);
        } else if ("browser".equalsIgnoreCase(source)) {
            c.setFontColor(ColorConstants.DARK_GRAY);
        }
        return c.setTextAlignment(TextAlignment.CENTER);
    }

    private void appendMap(Document doc, String label, Map<String, Object> map) {
        if (map == null || map.isEmpty()) return;
        doc.add(new Paragraph(label + ":").setBold());
        map.forEach((k, v) -> doc.add(new Paragraph("  " + k + ": " + v).setFontSize(10)));
    }

    private void line(Document doc, String label, String value) {
        if (value == null || value.isBlank()) return;
        doc.add(new Paragraph(label + ": " + value).setFontSize(10));
    }

    private String safe(String s) { return s == null ? "" : s; }
}
