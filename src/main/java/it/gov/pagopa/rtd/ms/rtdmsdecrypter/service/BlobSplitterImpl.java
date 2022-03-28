package it.gov.pagopa.rtd.ms.rtdmsdecrypter.service;

import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.BlobApplicationAware;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.springframework.stereotype.Service;

/**
 * Concrete implementation of a BlobSplitter interface.
 */
@Service
@Slf4j
public class BlobSplitterImpl implements BlobSplitter {

  /**
   * Method that spli the content of a blob in chunks of n lines.
   *
   * @param blob to be split.
   * @param n    max number of lines allowed in one blob chunk.
   * @return a list of blobs that represent the split blob.
   */
  public List<BlobApplicationAware> split(BlobApplicationAware blob, int n) {

    String blobPath = Path.of(blob.getTargetDir(), blob.getBlob() + ".decrypted").toString();

    ArrayList<BlobApplicationAware> blobSplit = new ArrayList<>();

    //Incremental integer for chunk numbering
    int chunkNum = 0;

    //Counter for current line number (from 0 to n)
    int i;

    try (
        LineIterator it = FileUtils.lineIterator(
            Path.of(blobPath).toFile(), "UTF-8")
    ) {
      while (it.hasNext()) {
        try (Writer writer = Channels.newWriter(new FileOutputStream(blobPath + "." + chunkNum,
                true).getChannel(),
            StandardCharsets.UTF_8)) {
          i = 0;
          while (i < n) {
            if (it.hasNext()) {
              String line = it.nextLine();
              writer.append(line).append("\n");
            } else {
              break;
            }
            i++;
          }
        }
        chunkNum++;
      }
    } catch (IOException e) {
      log.error("Missing file:{}", blobPath);
    }

    return blobSplit;
  }
}
