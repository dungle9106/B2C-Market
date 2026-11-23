package com.marketplace.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class ChatQueryTest {

    @Test
    void abbreviationsAndTeenCode() {
        assertEquals("minh can dien thoai duoi 5tr", ChatQuery.rewrite("mk can dt duoi 5tr"));
        assertEquals("kem chong nang cho da dau", ChatQuery.rewrite("kcn cho da dau"));
        assertEquals("co tai nghe bluetooth khong shop", ChatQuery.rewrite("co tn bluetooth ko shop"));
    }

    @Test
    void dhIsAnOrderOnlyWhenAboutDelivery() {
        assertTrue(ChatQuery.rewrite("dh cua mk giao chua").startsWith("don hang"));
        assertEquals("dong ho nam day da", ChatQuery.rewrite("dh nam day da"));
    }

    @Test
    void changeOfMindFillersGoButSonThoiStays() {
        assertEquals("khong loa di", ChatQuery.rewrite("khong, doi sang loa di"));
        assertEquals("lay iphone", ChatQuery.rewrite("thoi lay iphone"));
        assertEquals("duoi 5 trieu", ChatQuery.rewrite("duoi 5 trieu thoi"));
        assertEquals("son thoi mau do", ChatQuery.rewrite("son thoi mau do"));
    }

    @Test
    void typosBecomeCatalogueWords() {
        Set<String> vocab = new TreeSet<>(Set.of("dien", "thoai", "samsung", "khoac", "watch", "nhiet", "mascara"));
        assertEquals("dien thoai samsung", ChatQuery.correct("dien thaoi samsug", vocab, Set.of()));
        assertEquals("ao khoac", ChatQuery.correct("ao khaoc", vocab, Set.of()));
        assertEquals("apple watch", ChatQuery.correct("apple wach", vocab, Set.of()));
        assertEquals("mascara", ChatQuery.correct("mascra", vocab, Set.of()));
        // short words, kept words and words far from everything are left alone
        assertEquals("tai nge honda khong", ChatQuery.correct("tai nge honda khong", vocab, Set.of("khong")));
        // never into a stop word or a short word
        assertEquals("tu lanh", ChatQuery.correct("tu lanh", new TreeSet<>(Set.of("anh", "lan", "chanh")), Set.of("anh")));
    }

    @Test
    void swapCountsAsOneEdit() {
        assertEquals(1, ChatQuery.distance("thaoi", "thoai", 2));
        assertEquals(1, ChatQuery.distance("samsug", "samsung", 2));
        assertTrue(ChatQuery.distance("honda", "samsung", 1) > 1);
    }

    @Test
    void halfTheWordsMustMatch() {
        assertFalse(ChatQuery.enough(1, 3, false));   // "tu lanh samsung" -> a Samsung phone
        assertTrue(ChatQuery.enough(2, 3, false));
        assertTrue(ChatQuery.enough(1, 3, true));     // follow-up on an earlier subject
        assertFalse(ChatQuery.enough(0, 1, true));
    }

    @Test
    void smallTalk() {
        assertTrue(ChatQuery.offTopic("thoi tiet hom nay the nao"));
        assertFalse(ChatQuery.offTopic("tai nghe bluetooth"));
    }
}
