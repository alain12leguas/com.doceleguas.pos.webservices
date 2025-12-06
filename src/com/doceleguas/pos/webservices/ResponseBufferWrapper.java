package com.doceleguas.pos.webservices;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class ResponseBufferWrapper extends HttpServletResponseWrapper {

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final PrintWriter writer = new PrintWriter(buffer);

  public ResponseBufferWrapper(HttpServletResponse response) {
    super(response);
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    return writer;
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    throw new IllegalStateException("OutputStream no implementado, usa getWriter()");
  }

  public String getCapturedContent() {
    writer.flush();
    return buffer.toString();
  }
}
