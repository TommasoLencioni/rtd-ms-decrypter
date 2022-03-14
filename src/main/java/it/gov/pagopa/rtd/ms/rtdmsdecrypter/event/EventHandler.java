package it.gov.pagopa.rtd.ms.rtdmsdecrypter.event;

import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.BlobApplicationAware;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.BlobApplicationAware.Status;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.model.EventGridEvent;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.service.BlobRestConnectorImpl;
import it.gov.pagopa.rtd.ms.rtdmsdecrypter.service.DecrypterImpl;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * Component defining the processing steps in response to storage events.
 */
@Configuration
@Getter
public class EventHandler {

  /**
   * Constructor.
   *
   * @param decrypterImpl         an instance of a Decrypter
   * @param blobRestConnectorImpl an instance of a blobRestConnector
   * @return a consumer for Event Grid events
   */
  @Bean
  public Consumer<Message<List<EventGridEvent>>> blobStorageConsumer(DecrypterImpl decrypterImpl,
      BlobRestConnectorImpl blobRestConnectorImpl) {

    return message -> message.getPayload().stream()
        .filter(e -> "Microsoft.Storage.BlobCreated".equals(e.getEventType()))
        .map(EventGridEvent::getSubject)
        .map(BlobApplicationAware::new)
        .filter(b -> !BlobApplicationAware.Application.NOAPP.equals(b.getApp()))
        .map(blobRestConnectorImpl::get)
        .filter(b -> BlobApplicationAware.Status.DOWNLOADED.equals(b.getStatus()))
        .map(decrypterImpl::decrypt)
        .filter(b -> BlobApplicationAware.Status.DECRYPTED.equals(b.getStatus()))
        .map(blobRestConnectorImpl::put)
        .filter(b -> BlobApplicationAware.Status.UPLOADED.equals(b.getStatus()))
        .collect(Collectors.toList());
  }

}