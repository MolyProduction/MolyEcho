package com.module.notelycompose.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DohResolverTest {

    @Test
    fun `parses A records from real Google DoH response shape`() {
        // Verkürzte echte Antwort von https://8.8.8.8/resolve?name=huggingface.co&type=A
        val json = """{"Status":0,"TC":false,"Question":[{"name":"huggingface.co.","type":1}],""" +
            """"Answer":[{"name":"huggingface.co.","type":1,"TTL":60,"data":"52.222.136.89"},""" +
            """{"name":"huggingface.co.","type":1,"TTL":60,"data":"52.222.136.38"}],""" +
            """"Comment":"Response from 205.251.195.151."}"""
        assertEquals(listOf("52.222.136.89", "52.222.136.38"), DohResolver.parseDohAnswers(json))
    }

    @Test
    fun `ignores CNAME answers because their data is not an IPv4 literal`() {
        val json = """{"Answer":[{"name":"cdn.example.","type":5,"TTL":300,"data":"edge.example.net."},""" +
            """{"name":"edge.example.net.","type":1,"TTL":60,"data":"93.184.216.34"}]}"""
        assertEquals(listOf("93.184.216.34"), DohResolver.parseDohAnswers(json))
    }

    @Test
    fun `rejects out-of-range octets`() {
        val json = """{"Answer":[{"type":1,"data":"999.1.1.1"},{"type":1,"data":"10.0.0.1"}]}"""
        assertEquals(listOf("10.0.0.1"), DohResolver.parseDohAnswers(json))
    }

    @Test
    fun `deduplicates repeated addresses`() {
        val json = """{"Answer":[{"type":1,"data":"1.2.3.4"},{"type":1,"data":"1.2.3.4"}]}"""
        assertEquals(listOf("1.2.3.4"), DohResolver.parseDohAnswers(json))
    }

    @Test
    fun `empty or NXDOMAIN response yields empty list`() {
        assertTrue(DohResolver.parseDohAnswers("""{"Status":3,"Question":[{"name":"x.invalid.","type":1}]}""").isEmpty())
        assertTrue(DohResolver.parseDohAnswers("").isEmpty())
    }
}
