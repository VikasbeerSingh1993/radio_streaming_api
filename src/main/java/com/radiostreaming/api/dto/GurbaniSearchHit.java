package com.radiostreaming.api.dto;

public class GurbaniSearchHit {

    private long verseId;
    private String sourceCode;
    private Long shabadId;
    private Integer pageNo;
    private Integer lineNo;
    private Integer ang;
    private String gurmukhi;
    private String unicode;
    private String transliteration;
    private String translation;
    private String writer;
    private String raag;
    private Double score;
    private String matchMode;

    public long getVerseId() {
        return verseId;
    }

    public void setVerseId(long verseId) {
        this.verseId = verseId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public Long getShabadId() {
        return shabadId;
    }

    public void setShabadId(Long shabadId) {
        this.shabadId = shabadId;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Integer getAng() {
        return ang;
    }

    public void setAng(Integer ang) {
        this.ang = ang;
    }

    public String getGurmukhi() {
        return gurmukhi;
    }

    public void setGurmukhi(String gurmukhi) {
        this.gurmukhi = gurmukhi;
    }

    public String getUnicode() {
        return unicode;
    }

    public void setUnicode(String unicode) {
        this.unicode = unicode;
    }

    public String getTransliteration() {
        return transliteration;
    }

    public void setTransliteration(String transliteration) {
        this.transliteration = transliteration;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getWriter() {
        return writer;
    }

    public void setWriter(String writer) {
        this.writer = writer;
    }

    public String getRaag() {
        return raag;
    }

    public void setRaag(String raag) {
        this.raag = raag;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(String matchMode) {
        this.matchMode = matchMode;
    }
}
