package com.pkb.provider;

import com.pkb.common.error.BizException;
import com.pkb.provider.dto.OpenAiDtos.EmbeddingRequest;
import com.pkb.provider.dto.OpenAiDtos.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private final ModelConfigHolder holder;
    private final RestClient restClient;

    public OpenAiCompatibleEmbeddingProvider(ModelConfigHolder holder, RestClient.Builder builder) {
        this.holder = holder;
        this.restClient = builder.build();
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        var ep = holder.currentEmbedding();
        try {
            EmbeddingResponse resp = restClient.post()
                    .uri(ep.baseUrl() + "/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + ep.apiKey())
                    .body(new EmbeddingRequest(ep.model(), texts))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (resp == null || resp.data() == null || resp.data().size() != texts.size()) {
                throw new BizException("向量化返回数量不匹配，请稍后重试");
            }
            return resp.data().stream().map(entry -> {
                float[] v = new float[entry.embedding().size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = entry.embedding().get(i).floatValue();
                }
                return v;
            }).toList();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("调用向量化服务失败：" + e.getMessage());
        }
    }

    @Override
    public String modelId() {
        return holder.currentEmbedding().model();
    }
}
