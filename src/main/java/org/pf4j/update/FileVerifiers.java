/*
 * Copyright 2018 Decebal Suiu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pf4j.update;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Collection of standard file verifiers.
 * Other implementations could e.g. be Antivirus checks, scanning for forbidden jar files,
 * checking digital signatures etc.
 */
public class FileVerifiers {
  /**
   * All default verifiers
   */
  public static final List<FileVerifier> ALL_DEFAULT_FILE_VERIFIERS = Arrays.asList(
      new FileVerifiers.BasicVerifier(),
      new Sha512SumVerifier());

  /**
   * Verifies that the SHA512 checksum of a downloaded file equals the checksum given in
   * the plugins.json descriptor. This helps validate that the file downloaded is exactly
   * the same as intended. Especially useful when dealing with meta repositories pointing
   * to S3 or other 3rd party download locations that could have been tampered with.
   */
  public static class Sha512SumVerifier implements FileVerifier {
    private static final Logger log = LoggerFactory.getLogger(SimpleFileDownloader.class);

    /**
     * Verifies a plugin release according to certain rules
     *
     * @param context the file verifier context object
     * @param file    the path to the downloaded file itself
     * @throws IOException     if there was a problem accessing file
     * @throws VerifyException in case of problems verifying the file
     */
    @Override
    public void verify(Context context, Path file) throws VerifyException, IOException {
      String expectedSha512sum;
      try {
        if (context.sha512sum == null) {
          log.debug("No sha512 checksum specified, skipping verification");
          return;
        } else if (context.sha512sum.equalsIgnoreCase(".sha512")) {
          String url = context.url.substring(0, context.url.lastIndexOf(".")) + ".sha512";;
          expectedSha512sum = getUrlContents(url).split(" ")[0].trim();
        } else if (context.sha512sum.startsWith("http")) {
          expectedSha512sum = getUrlContents(context.sha512sum).split(" ")[0].trim();
        } else {
          expectedSha512sum = context.sha512sum;
        }
      } catch (IOException e) {
        throw new VerifyException(e, "SHA512 checksum verification failed, could not download SHA512 file ({})", context.sha512sum);
      }

      log.debug("Verifying sha512 checksum of file {}", file.getFileName());
      String actualSha512sum = DigestUtils.sha512Hex(Files.newInputStream(file));
      if (actualSha512sum.equalsIgnoreCase(expectedSha512sum)) {
          log.debug("Checksum OK");
          return;
      }
      throw new VerifyException("SHA512 checksum of downloaded file " + file.getFileName()
          + " does not match that from plugin descriptor. Got " + actualSha512sum
          + " but expected " + expectedSha512sum);
    }

    private String getUrlContents(String url) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new URL(url).openStream()));
        return reader.readLine();
    }
  }

  /**
   * Verifies that the file exists is a regular file and has a non-null size
   */
  public static class BasicVerifier implements FileVerifier {
    /**
     * Verifies a plugin release according to certain rules
     *
     * @param context the file verifier context object
     * @param file    the path to the downloaded file itself
     * @throws IOException     if there was a problem accessing file
     * @throws VerifyException in case of problems verifying the file
     */
    @Override
    public void verify(Context context, Path file) throws VerifyException, IOException {
      if (!Files.isRegularFile(file) || Files.size(file) == 0) {
        throw new VerifyException("File {} is not a regular file or has size 0", file);
      }
    }
  }
}
