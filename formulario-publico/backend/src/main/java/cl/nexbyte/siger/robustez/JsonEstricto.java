package cl.nexbyte.siger.robustez;
import org.springframework.context.annotation.*;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
@Configuration
public class JsonEstricto {
    @Bean JsonFactoryBuilderCustomizer jsonLimites(){return builder->builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(5000).maxNumberLength(30).build());}
}
