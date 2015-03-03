/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.MessageConstraintException;
import org.apache.http.ParseException;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.config.MessageConstraints;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicLineParserHC4;
import org.apache.http.message.LineParser;
import org.apache.http.params.HttpParamConfig;
import org.apache.http.params.HttpParams;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

/**
 * Abstract base class for HTTP message parsers that obtain input from
 * an instance of {@link org.apache.http.io.SessionInputBuffer}.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public abstract class AbstractMessageParserHC4<T extends HttpMessage> implements HttpMessageParser {

    private static final int HEAD_LINE    = 0;
    private static final int HEADERS      = 1;

    private final SessionInputBuffer sessionBuffer;
    private final MessageConstraints messageConstraints;
    private final List<CharArrayBuffer> headerLines;
    protected final LineParser lineParser;

    private int state;
    private T message;

    /**
     * Creates an instance of AbstractMessageParserHC4.
     *
     * @param buffer the session input buffer.
     * @param parser the line parser.
     * @param params HTTP parameters.
     *
     * @deprecated (4.3) use {@link org.apache.http.impl.io.AbstractMessageParserHC4#AbstractMessageParserHC4(org.apache.http.io.SessionInputBuffer,
     *   org.apache.http.message.LineParser, org.apache.http.config.MessageConstraints)}
     */
    @Deprecated
    public AbstractMessageParserHC4(
            final SessionInputBuffer buffer,
            final LineParser parser,
            final HttpParams params) {
        super();
        Args.notNull(buffer, "Session input buffer");
        Args.notNull(params, "HTTP parameters");
        this.sessionBuffer = buffer;
        this.messageConstraints = HttpParamConfig.getMessageConstraints(params);
        this.lineParser = (parser != null) ? parser : BasicLineParserHC4.INSTANCE;
        this.headerLines = new ArrayList<CharArrayBuffer>();
        this.state = HEAD_LINE;
    }

    /**
     * Creates new instance of AbstractMessageParserHC4.
     *
     * @param buffer the session input buffer.
     * @param lineParser the line parser. If <code>null</code> {@link org.apache.http.message.BasicLineParserHC4#INSTANCE}
     *   will be used.
     * @param constraints the message constraints. If <code>null</code>
     *   {@link org.apache.http.config.MessageConstraints#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public AbstractMessageParserHC4(
            final SessionInputBuffer buffer,
            final LineParser lineParser,
            final MessageConstraints constraints) {
        super();
        this.sessionBuffer = Args.notNull(buffer, "Session input buffer");
        this.lineParser = lineParser != null ? lineParser : BasicLineParserHC4.INSTANCE;
        this.messageConstraints = constraints != null ? constraints : MessageConstraints.DEFAULT;
        this.headerLines = new ArrayList<CharArrayBuffer>();
        this.state = HEAD_LINE;
    }

    /**
     * Parses HTTP headers from the data receiver stream according to the generic
     * format as given in Section 3.1 of RFC 822, RFC-2616 Section 4 and 19.3.
     *
     * @param inbuffer Session input buffer
     * @param maxHeaderCount maximum number of headers allowed. If the number
     *  of headers received from the data stream exceeds maxCount value, an
     *  IOException will be thrown. Setting this parameter to a negative value
     *  or zero will disable the check.
     * @param maxLineLen maximum number of characters for a header line,
     *  including the continuation lines. Setting this parameter to a negative
     *  value or zero will disable the check.
     * @return array of HTTP headers
     * @param parser line parser to use. Can be <code>null</code>, in which case
     *  the default implementation of this interface will be used.
     *
     * @throws java.io.IOException in case of an I/O error
     * @throws org.apache.http.HttpException in case of HTTP protocol violation
     */
    public static Header[] parseHeaders(
            final SessionInputBuffer inbuffer,
            final int maxHeaderCount,
            final int maxLineLen,
            final LineParser parser) throws HttpException, IOException {
        final List<CharArrayBuffer> headerLines = new ArrayList<CharArrayBuffer>();
        return parseHeaders(inbuffer, maxHeaderCount, maxLineLen,
                parser != null ? parser : BasicLineParserHC4.INSTANCE,
                headerLines);
    }

    /**
     * Parses HTTP headers from the data receiver stream according to the generic
     * format as given in Section 3.1 of RFC 822, RFC-2616 Section 4 and 19.3.
     *
     * @param inbuffer Session input buffer
     * @param maxHeaderCount maximum number of headers allowed. If the number
     *  of headers received from the data stream exceeds maxCount value, an
     *  IOException will be thrown. Setting this parameter to a negative value
     *  or zero will disable the check.
     * @param maxLineLen maximum number of characters for a header line,
     *  including the continuation lines. Setting this parameter to a negative
     *  value or zero will disable the check.
     * @param parser line parser to use.
     * @param headerLines List of header lines. This list will be used to store
     *   intermediate results. This makes it possible to resume parsing of
     *   headers in case of a {@link java.io.InterruptedIOException}.
     *
     * @return array of HTTP headers
     *
     * @throws java.io.IOException in case of an I/O error
     * @throws org.apache.http.HttpException in case of HTTP protocol violation
     *
     * @since 4.1
     */
    public static Header[] parseHeaders(
            final SessionInputBuffer inbuffer,
            final int maxHeaderCount,
            final int maxLineLen,
            final LineParser parser,
            final List<CharArrayBuffer> headerLines) throws HttpException, IOException {
        Args.notNull(inbuffer, "Session input buffer");
        Args.notNull(parser, "Line parser");
        Args.notNull(headerLines, "Header line list");

        CharArrayBuffer current = null;
        CharArrayBuffer previous = null;
        for (;;) {
            if (current == null) {
                current = new CharArrayBuffer(64);
            } else {
                current.clear();
            }
            final int l = inbuffer.readLine(current);
            if (l == -1 || current.length() < 1) {
                break;
            }
            // Parse the header name and value
            // Check for folded headers first
            // Detect LWS-char see HTTP/1.0 or HTTP/1.1 Section 2.2
            // discussion on folded headers
            if ((current.charAt(0) == ' ' || current.charAt(0) == '\t') && previous != null) {
                // we have continuation folded header
                // so append value
                int i = 0;
                while (i < current.length()) {
                    final char ch = current.charAt(i);
                    if (ch != ' ' && ch != '\t') {
                        break;
                    }
                    i++;
                }
                if (maxLineLen > 0
                        && previous.length() + 1 + current.length() - i > maxLineLen) {
                    throw new MessageConstraintException("Maximum line length limit exceeded");
                }
                previous.append(' ');
                previous.append(current, i, current.length() - i);
            } else {
                headerLines.add(current);
                previous = current;
                current = null;
            }
            if (maxHeaderCount > 0 && headerLines.size() >= maxHeaderCount) {
                throw new MessageConstraintException("Maximum header count exceeded");
            }
        }
        final Header[] headers = new Header[headerLines.size()];
        for (int i = 0; i < headerLines.size(); i++) {
            final CharArrayBuffer buffer = headerLines.get(i);
            try {
                headers[i] = parser.parseHeader(buffer);
            } catch (final ParseException ex) {
                throw new ProtocolException(ex.getMessage());
            }
        }
        return headers;
    }

    /**
     * Subclasses must override this method to generate an instance of
     * {@link org.apache.http.HttpMessage} based on the initial input from the session buffer.
     * <p>
     * Usually this method is expected to read just the very first line or
     * the very first valid from the data stream and based on the input generate
     * an appropriate instance of {@link org.apache.http.HttpMessage}.
     *
     * @param sessionBuffer the session input buffer.
     * @return HTTP message based on the input from the session buffer.
     * @throws java.io.IOException in case of an I/O error.
     * @throws org.apache.http.HttpException in case of HTTP protocol violation.
     * @throws org.apache.http.ParseException in case of a parse error.
     */
    protected abstract T parseHead(SessionInputBuffer sessionBuffer)
        throws IOException, HttpException, ParseException;

    public T parse() throws IOException, HttpException {
        final int st = this.state;
        switch (st) {
        case HEAD_LINE:
            try {
                this.message = parseHead(this.sessionBuffer);
            } catch (final ParseException px) {
                throw new ProtocolException(px.getMessage(), px);
            }
            this.state = HEADERS;
            //$FALL-THROUGH$
        case HEADERS:
            final Header[] headers = AbstractMessageParserHC4.parseHeaders(
                    this.sessionBuffer,
                    this.messageConstraints.getMaxHeaderCount(),
                    this.messageConstraints.getMaxLineLength(),
                    this.lineParser,
                    this.headerLines);
            this.message.setHeaders(headers);
            final T result = this.message;
            this.message = null;
            this.headerLines.clear();
            this.state = HEAD_LINE;
            return result;
        default:
            throw new IllegalStateException("Inconsistent parser state");
        }
    }

}
