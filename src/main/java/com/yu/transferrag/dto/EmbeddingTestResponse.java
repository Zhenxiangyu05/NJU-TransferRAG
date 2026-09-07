package com.yu.transferrag.dto;

public class EmbeddingTestResponse {

    private String text;
    private int dimension;
    private float[] preview;

    public EmbeddingTestResponse(String text, int dimension, float[] preview) {
        this.text = text;
        this.dimension = dimension;
        this.preview = preview;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public float[] getPreview() {
        return preview;
    }

    public void setPreview(float[] preview) {
        this.preview = preview;
    }
}
