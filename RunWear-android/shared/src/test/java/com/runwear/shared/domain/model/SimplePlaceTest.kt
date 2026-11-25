package com.runwear.shared.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WS-4 (RC-4, D-08/D-09): the simple-place model. Fixtures carry long
 * place names and multi-word regions on purpose — the simple form is
 * what every place-bearing surface renders, and no raw geocode string
 * may survive derivation.
 */
class SimplePlaceTest {

    @Test
    fun `city plus full state name renders city plus USPS abbreviation`() {
        assertEquals("New York, NY", SimplePlace.fromGeocode("New York", "New York", "us").display)
        assertEquals("Kansas City, MO", SimplePlace.fromGeocode("Kansas City", "Missouri", "us").display)
        assertEquals("San Francisco, CA", SimplePlace.fromGeocode("San Francisco", "California", "us").display)
    }

    @Test
    fun `multi-word US regions abbreviate`() {
        assertEquals(
            "Winston-Salem, NC",
            SimplePlace.fromGeocode("Winston-Salem", "North Carolina", "us").display
        )
        assertEquals(
            "Truth or Consequences, NM",
            SimplePlace.fromGeocode("Truth or Consequences", "New Mexico", "us").display
        )
        assertEquals(
            "Concord, NH",
            SimplePlace.fromGeocode("Concord", "New Hampshire", "us").display
        )
        assertEquals(
            "Washington, DC",
            SimplePlace.fromGeocode("Washington", "District of Columbia", "us").display
        )
    }

    @Test
    fun `canadian and australian multi-word regions abbreviate`() {
        assertEquals("Toronto, ON", SimplePlace.fromGeocode("Toronto", "Ontario", "ca").display)
        assertEquals(
            "Vancouver, BC",
            SimplePlace.fromGeocode("Vancouver", "British Columbia", "ca").display
        )
        assertEquals(
            "Sydney, NSW",
            SimplePlace.fromGeocode("Sydney", "New South Wales", "au").display
        )
        assertEquals(
            "Brisbane, QLD",
            SimplePlace.fromGeocode("Brisbane", "Queensland", "au").display
        )
    }

    @Test
    fun `long place names keep the full city and the abbreviated region`() {
        // The city is never truncated; only the region shortens.
        val place = SimplePlace.fromGeocode(
            "San Miguel de Allende", "Guanajuato", "mx"
        )
        assertEquals("San Miguel de Allende, Guanajuato", place.display)
        val us = SimplePlace.fromRawDisplayString("Truth or Consequences, New Mexico")
        assertEquals("Truth or Consequences", us.city)
        assertEquals("NM", us.region)
    }

    @Test
    fun `raw geocode strings normalize and simple strings are idempotent`() {
        assertEquals("New York, NY", SimplePlace.fromRawDisplayString("New York, New York").display)
        assertEquals("New York, NY", SimplePlace.fromRawDisplayString("New York, NY").display)
        assertEquals(
            "New York, NY",
            SimplePlace.fromRawDisplayString(SimplePlace.fromRawDisplayString("New York, New York").display).display
        )
        assertEquals("Aspen, CO", SimplePlace.fromRawDisplayString("Aspen, Colorado").display)
        assertEquals("Aspen, CO", SimplePlace.fromRawDisplayString("Aspen, CO").display)
    }

    @Test
    fun `a region identical to the city is dropped when it cannot abbreviate`() {
        assertEquals("Singapore", SimplePlace.fromGeocode("Singapore", "Singapore", "sg").display)
        // …but a mappable twin name still abbreviates (New York is the case that matters).
        assertEquals("New York, NY", SimplePlace.fromGeocode("New York", "New York", "us").display)
    }

    @Test
    fun `unmapped multi-word regions fall back to the city alone`() {
        assertEquals("Paris", SimplePlace.fromGeocode("Paris", "Île-de-France", "fr").display)
    }

    @Test
    fun `single-word unmapped regions stay - they are already simple`() {
        assertEquals("London, England", SimplePlace.fromGeocode("London", "England", "gb").display)
        assertEquals("Munich, Bavaria", SimplePlace.fromGeocode("Munich", "Bavaria", "de").display)
    }

    @Test
    fun `no region renders the city alone`() {
        assertEquals("Hong Kong", SimplePlace.fromGeocode("Hong Kong", null, "hk").display)
        assertEquals("Hong Kong", SimplePlace.fromGeocode("Hong Kong", "", "hk").display)
        assertEquals("Hong Kong", SimplePlace.fromRawDisplayString("Hong Kong").display)
    }

    @Test
    fun `already abbreviated regions pass through upper-cased`() {
        assertEquals("NY", SimplePlace.abbreviateRegion("ny", "us"))
        assertEquals("NY", SimplePlace.abbreviateRegion("NY", "us"))
        assertEquals("NSW", SimplePlace.abbreviateRegion("NSW", "au"))
        assertNull(SimplePlace.abbreviateRegion("Île-de-France", "fr"))
    }

    @Test
    fun `sentinels and blanks pass through raw normalization unchanged`() {
        assertEquals("Your Location", SimplePlace.fromRawDisplayString("Your Location").display)
        assertEquals("Getting location...", SimplePlace.fromRawDisplayString("Getting location...").display)
    }
}
