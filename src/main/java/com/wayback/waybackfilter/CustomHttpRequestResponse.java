package com.example.waybackextension; // Make sure this matches the package in WaybackExtension.java

import burp.IHttpRequestResponse;
import burp.IHttpService;

public class CustomHttpRequestResponse implements IHttpRequestResponse {
    private byte[] request;
    private byte[] response;
    private IHttpService httpService;
    private String highlight;
    private String comment;

    public CustomHttpRequestResponse(byte[] request, byte[] response, IHttpService httpService, String highlight, String comment) {
        this.request = request;
        this.response = response;
        this.httpService = httpService;
        this.highlight = highlight;
        this.comment = comment;
    }

    @Override
    public byte[] getRequest() {
        return request;
    }

    @Override
    public void setRequest(byte[] message) {
        this.request = message;
    }

    @Override
    public byte[] getResponse() {
        return response;
    }

    @Override
    public void setResponse(byte[] message) {
        this.response = message;
    }

    @Override
    public IHttpService getHttpService() {
        return httpService;
    }

    @Override
    public void setHttpService(IHttpService httpService) {
        this.httpService = httpService;
    }

    @Override
    public String getHighlight() {
        return highlight;
    }

    @Override
    public void setHighlight(String color) {
        this.highlight = color;
    }

    @Override
    public String getComment() {
        return comment;
    }

    @Override
    public void setComment(String comment) {
        this.comment = comment;
    }
}

