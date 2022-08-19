package it.gov.pagopa.rtd.ms.rtdmsdecrypter.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.BlobApplicationAware;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.BlobApplicationAware.Application;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.BlobApplicationAware.Status;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ContextConfiguration(classes = {BlobSplitterImpl.class})
@TestPropertySource(value = {"classpath:application-nokafka.yml"}, inheritProperties = false)
@ExtendWith(OutputCaptureExtension.class)
class BlobSplitterTest {

  @Autowired
  BlobSplitterImpl blobSplitterImpl;

  @Value("${decrypt.resources.base.path}")
  String resources;

  @Value("${decrypt.resources.base.path}/tmp")
  String tmpDirectory;

  String container = "rtd-transactions-32489876908u74bh781e2db57k098c5ad00000000000";
  String blobNameRTD = "CSTAR.99999.TRNLOG.20220419.121045.001.csv";

  String blobNameTAE = "ADE.99999.TRNLOG.20220721.095718.001.csv";

  String blobNameTAEEmpty = "ADE.00000.TRNLOG.20220721.095718.001.csv";

  BlobApplicationAware fakeBlobRTD;

  BlobApplicationAware fakeBlobTAE;

  BlobApplicationAware fakeBlobTAEEmpty;

  @BeforeEach
  void setUp() throws IOException {

    //Create the decrypted file for RTD
    File decryptedFile = Path.of(tmpDirectory, blobNameRTD).toFile();
    decryptedFile.getParentFile().mkdirs();
    decryptedFile.createNewFile();
    FileOutputStream decryptedStream = new FileOutputStream(
        Path.of(tmpDirectory, blobNameRTD + ".decrypted").toString());
    Files.copy(Path.of(resources, blobNameRTD), decryptedStream);

    //Instantiate a fake RTD blob with clear text content
    fakeBlobRTD = new BlobApplicationAware(
        "/blobServices/default/containers/" + container + "/blobs/" + blobNameRTD);
    fakeBlobRTD.setTargetDir(tmpDirectory);
    fakeBlobRTD.setStatus(Status.DECRYPTED);
    fakeBlobRTD.setApp(Application.RTD);

    //Create the decrypted file for TAE
    File decryptedFileAggregates = Path.of(tmpDirectory, blobNameTAE).toFile();
    decryptedFileAggregates.getParentFile().mkdirs();
    decryptedFileAggregates.createNewFile();
    FileOutputStream decryptedStreamAggregates = new FileOutputStream(
        Path.of(tmpDirectory, blobNameTAE + ".decrypted").toString());
    Files.copy(Path.of(resources, blobNameTAE), decryptedStreamAggregates);

    //Instantiate a fake TAE blob with clear text content
    fakeBlobTAE = new BlobApplicationAware(
        "/blobServices/default/containers/" + container + "/blobs/" + blobNameTAE);
    fakeBlobTAE.setTargetDir(tmpDirectory);
    fakeBlobTAE.setStatus(Status.DECRYPTED);
    fakeBlobTAE.setApp(Application.ADE);

    //Create the decrypted empty file for TAE
    File decryptedFileAggregatesEmpty = Path.of(tmpDirectory, blobNameTAEEmpty).toFile();
    decryptedFileAggregatesEmpty.getParentFile().mkdirs();
    decryptedFileAggregatesEmpty.createNewFile();
    FileOutputStream decryptedStreamAggregatesEmpty = new FileOutputStream(
        Path.of(tmpDirectory, blobNameTAEEmpty + ".decrypted").toString());
    Files.copy(Path.of(resources, blobNameTAEEmpty), decryptedStreamAggregatesEmpty);

    //Instantiate a fake TAE blob with clear text content
    fakeBlobTAEEmpty = new BlobApplicationAware(
        "/blobServices/default/containers/" + container + "/blobs/" + blobNameTAEEmpty);
    fakeBlobTAEEmpty.setTargetDir(tmpDirectory);
    fakeBlobTAEEmpty.setStatus(Status.DECRYPTED);
    fakeBlobTAEEmpty.setApp(Application.ADE);
  }

  @AfterEach
  void cleanTmpFiles() throws IOException {
    FileUtils.deleteDirectory(Path.of(tmpDirectory).toFile());
  }

  @Test
  void shouldSplitRTD(CapturedOutput output) {

    blobSplitterImpl.setLineThreshold(1);

    Stream<BlobApplicationAware> chunks = blobSplitterImpl.split(fakeBlobRTD);
    Iterable<BlobApplicationAware> iterable = chunks::iterator;
    int i = 0;
    for (BlobApplicationAware b : iterable) {
      assertEquals(Status.SPLIT, b.getStatus());
      assertEquals(blobNameRTD + "." + i + ".decrypted", b.getBlob());
      i++;
    }
    assertEquals(3, i);
    assertThat(output.getOut(), containsString("Obtained 3 chunk/s from blob:"));

  }

  @Test
  void shouldSplitTAE() {
    //Instantiate a fake blob with clear text content
    blobSplitterImpl.setLineThreshold(1);

    Stream<BlobApplicationAware> chunks = blobSplitterImpl.split(fakeBlobTAE);
    Iterable<BlobApplicationAware> iterable = chunks::iterator;
    int i = 0;
    for (BlobApplicationAware b : iterable) {
      assertEquals(Status.SPLIT, b.getStatus());
      assertEquals(
          "AGGADE." + fakeBlobTAE.getSenderCode() + "." + fakeBlobTAE.getFileCreationDate() + "."
              + fakeBlobTAE.getFileCreationTime() + "." + fakeBlobTAE.getFlowNumber() + "." + i,
          b.getBlob());
      i++;
    }
    assertEquals(4, i);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;978;99999;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n"
      + "\n"
      + "00000;00;2022-07-21;2022-07-20;17;249135;978;99999;8894738909374375872;4759769053262163701;00000000005;00000000005;00",
      ";00;2022-07-21;2022-07-21;77;249135;978;99999;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;;2022-07-21;2022-07-21;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;001;2022-07-21;2022-07-21;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;;2022-07-21;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-;2022-07-21;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;-1;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;-1;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;-1;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;EUR;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;978;;8894738909374375872;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;978;00000;;4759769053262163701;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;978;00000;8894738909374375872;;00000000003;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;978;00000;8894738909374375872;4759769053262163701;;00000000003;00\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;0\n",
      "00000;00;2022-07-21;2022-07-21;77;249135;978;00000;8894738909374375872;4759769053262163701;00000000003;00000000003;000\n"})
  void shouldFailValidateTAE(String malformedAggregateRecord) throws IOException {
    Files.write(Path.of(tmpDirectory, blobNameTAEEmpty + ".decrypted"),
        malformedAggregateRecord.getBytes(), StandardOpenOption.APPEND);

    //Instantiate a fake blob with clear text content
    blobSplitterImpl.setLineThreshold(1);

    Stream<BlobApplicationAware> chunks = blobSplitterImpl.split(fakeBlobTAEEmpty);
    Iterable<BlobApplicationAware> iterable = chunks::iterator;
    int i = 0;
    for (BlobApplicationAware b : iterable) {
      assertEquals(Status.DECRYPTED, b.getStatus());
      assertEquals(fakeBlobTAEEmpty.getBlob(), b.getBlob());
      i++;
    }
    assertEquals(1, i);
  }

  //This test, contrary to the previous one, tests the scenario where the file run out of lines
  // before reaching the threshold.
  @Test
  void shouldSplitReminder(CapturedOutput output) {

    blobSplitterImpl.setLineThreshold(2);

    Stream<BlobApplicationAware> chunks = blobSplitterImpl.split(fakeBlobRTD);
    Iterable<BlobApplicationAware> iterable = chunks::iterator;
    int i = 0;
    for (BlobApplicationAware b : iterable) {
      assertEquals(Status.SPLIT, b.getStatus());
      assertEquals(blobNameRTD + "." + i + ".decrypted", b.getBlob());
      i++;
    }
    assertEquals(2, i);
    assertThat(output.getOut(), containsString("Obtained 2 chunk/s from blob:"));

  }

  @Test
  void shouldNotSplitMissingFile(CapturedOutput output) {

    //Set the wrong directory for locating the decrypted fake blob
    fakeBlobRTD.setTargetDir("pippo");
    fakeBlobRTD.setStatus(BlobApplicationAware.Status.DOWNLOADED);
    blobSplitterImpl.setLineThreshold(1);

    blobSplitterImpl.split(fakeBlobRTD);
    assertThat(output.getOut(), containsString("Missing blob file:"));

  }

}

