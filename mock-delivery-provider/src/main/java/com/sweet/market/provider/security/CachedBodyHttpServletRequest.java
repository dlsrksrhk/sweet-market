package com.sweet.market.provider.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;
    private final Charset readerCharset;

    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
        super(request);
        this.cachedBody = cachedBody;
        this.readerCharset = readerCharset(request.getCharacterEncoding());
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        ByteArrayInputStream input = new ByteArrayInputStream(cachedBody);
        return new BufferedReader(new InputStreamReader(input, readerCharset));
    }

    @Override
    public int getContentLength() {
        return cachedBody.length;
    }

    @Override
    public long getContentLengthLong() {
        return cachedBody.length;
    }

    private static Charset readerCharset(String characterEncoding) {
        if (characterEncoding == null) {
            return StandardCharsets.ISO_8859_1;
        }
        return Charset.forName(characterEncoding);
    }

    private static final class CachedBodyInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private CachedBodyInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Spring MVC consumes the cached body synchronously.
        }
    }
}
