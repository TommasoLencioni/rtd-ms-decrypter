package it.gov.pagopa.rtd.ms.rtdmsdecrypter.event;

import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.BlobApplicationAware;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.EventGridEvent;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.service.BlobRestConnectorImpl;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.service.BlobSplitterImpl;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.service.BlobVerifierImpl;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.service.DecrypterImpl;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * Component defining the processing steps in response to storage events.
 */
@Configuration
@Getter
@Slf4j
public class EventHandler {

  @Value("${decrypt.enableChunkUpload}")
  private boolean isChunkUploadEnabled;

  /**
   * Constructor.
   *
   * @param decrypterImpl         an instance of a Decrypter
   * @param blobRestConnectorImpl an instance of a BlobRestConnector
   * @param blobSplitterImpl      an instance of a BlobSplitter
   * @param blobVerifierImpl      an instance of a BlobVerifier
   * @return a consumer for Event Grid events
   */
  @Bean
  public Consumer<Message<List<EventGridEvent>>> blobStorageConsumer(DecrypterImpl decrypterImpl,
      BlobRestConnectorImpl blobRestConnectorImpl, BlobSplitterImpl blobSplitterImpl,
      BlobVerifierImpl blobVerifierImpl) {

    log.info("Chunks upload enabled: {}", isChunkUploadEnabled);

    return message -> {
      List<BlobApplicationAware> verifiedChunks = message.getPayload().stream()
          .filter(e -> "Microsoft.Storage.BlobCreated".equals(e.getEventType()))
          .map(EventGridEvent::getSubject)
          .map(BlobApplicationAware::new)
          .filter(b -> !BlobApplicationAware.Application.NOAPP.equals(b.getApp()))
          .map(blobRestConnectorImpl::get)
          .filter(b -> BlobApplicationAware.Status.DOWNLOADED.equals(b.getStatus()))
          .map(decrypterImpl::decrypt)
          .filter(b -> BlobApplicationAware.Status.DECRYPTED.equals(b.getStatus()))
          .flatMap(blobSplitterImpl::split)
          .filter(b -> BlobApplicationAware.Status.SPLIT.equals(b.getStatus()))
          .map(blobVerifierImpl::verify)
          .filter(b -> BlobApplicationAware.Status.VERIFIED.equals(b.getStatus()))
          .collect(Collectors.toList());

      int numberOfVerifiedChunks = verifiedChunks.size();

      List<BlobApplicationAware> uploadedChunks = verifiedChunks.stream()
          .filter(b -> b.chunkNumberCheck(numberOfVerifiedChunks))
          .map(b -> isChunkUploadEnabled ? blobRestConnectorImpl.put(b) : b)
          .filter(b -> BlobApplicationAware.Status.UPLOADED.equals(b.getStatus()))
          .map(BlobApplicationAware::localCleanup)
          .collect(Collectors.toList());

      if (uploadedChunks.size() != numberOfVerifiedChunks) {
        log.error("Not all chunks are verified, no chunks will be uploaded");
      }

      List<BlobApplicationAware> handledChunks = verifiedChunks.stream()
          .filter(b -> BlobApplicationAware.Status.DELETED.equals(b.getStatus()))
          .collect(Collectors.toList());

      if (!handledChunks.isEmpty() && handledChunks.size() == numberOfVerifiedChunks) {
        log.info("Correctly handled blob: {}", handledChunks.get(0).getOriginalBlobName());
      }
    };
  }
}
