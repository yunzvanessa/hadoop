/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hadoop.fs.s3a.prefetch;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.impl.prefetch.BufferData;
import org.apache.hadoop.fs.s3a.S3AInputStream;
import org.apache.hadoop.fs.s3a.S3AReadOpContext;
import org.apache.hadoop.fs.s3a.S3ObjectAttributes;
import org.apache.hadoop.fs.s3a.statistics.S3AInputStreamStatistics;

/**
 * Provides an {@code InputStream} that allows reading from an S3 file.
 * The entire file is read into memory before reads can begin.
 *
 * Use of this class is recommended only for small files that can fit
 * entirely in memory.
 */
public class S3AInMemoryInputStream extends S3ARemoteInputStream {

  private static final Logger LOG = LoggerFactory.getLogger(
      S3AInMemoryInputStream.class);

  private ByteBuffer buffer;

  /**
   * Initializes a new instance of the {@code S3AInMemoryInputStream} class.
   *
   * @param context read-specific operation context.
   * @param s3Attributes attributes of the S3 object being read.
   * @param client callbacks used for interacting with the underlying S3 client.
   * @param streamStatistics statistics for this stream.
   *
   * @throws IllegalArgumentException if context is null.
   * @throws IllegalArgumentException if s3Attributes is null.
   * @throws IllegalArgumentException if client is null.
   */
  public S3AInMemoryInputStream(
      S3AReadOpContext context,
      S3ObjectAttributes s3Attributes,
      S3AInputStream.InputStreamCallbacks client,
      S3AInputStreamStatistics streamStatistics) {
    super(context, s3Attributes, client, streamStatistics);
    int fileSize = (int) s3Attributes.getLen();
    this.buffer = ByteBuffer.allocate(fileSize);
    LOG.debug("Created in-memory input stream for {} (size = {})",
        getName(), fileSize);
  }

  /**
   * Ensures that a non-empty valid buffer is available for immediate reading.
   * It returns true when at least one such buffer is available for reading.
   * It returns false on reaching the end of the stream.
   *
   * @return true if at least one such buffer is available for reading, false otherwise.
   */
  @Override
  protected boolean ensureCurrentBuffer() throws IOException {
    if (isClosed()) {
      return false;
    }

    if (getBlockData().getFileSize() == 0) {
      return false;
    }

    if (!getFilePosition().isValid()) {
      buffer.clear();
      int numBytesRead =
          getReader().read(buffer, 0, buffer.capacity());
      if (numBytesRead <= 0) {
        return false;
      }
      BufferData data = new BufferData(0, buffer);
      getFilePosition().setData(data, 0, getSeekTargetPos());
    }

    return getFilePosition().buffer().hasRemaining();
  }
}
