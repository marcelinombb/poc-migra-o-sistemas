package br.com.legado.contracts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudEvent<T> {
    private String specversion;
    private String id;
    private String source;
    private String type;
    private String subject;
    private Instant time;
    private String datacontenttype;
    private T data;
}
