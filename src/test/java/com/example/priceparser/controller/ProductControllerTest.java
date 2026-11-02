package com.example.priceparser.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.priceparser.service.PageLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Вместо настоящей загрузки страниц подставляем заглушку, чтобы тесты не ходили в интернет.
 */
@SpringBootTest(properties = {
        "parser.results-file=target/test-data/results.jsonl",
        "parser.event-log-file=target/test-data/parser-events.log",
        "parser.scheduler.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:controller-test"
})
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PageLoader pageLoader;

    @Test
    void parseProductAndGetAnswer() throws Exception {
        String url = "https://www.labirint.ru/books/982366/";
        String html = "<html><body><h1>Волосы Вероники</h1>"
                + "<meta itemprop=\"price\" content=\"618\"><meta itemprop=\"priceCurrency\" content=\"RUB\">"
                + "</body></html>";
        when(pageLoader.load(url)).thenReturn(new PageLoader.LoadedPage(html, null));

        mockMvc.perform(post("/api/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Волосы Вероники"))
                .andExpect(jsonPath("$.price").value(618))
                .andExpect(jsonPath("$.currency").value("RUB"));

        mockMvc.perform(get("/answer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Волосы Вероники"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void wrongSortReturns400() throws Exception {
        mockMvc.perform(get("/answer").param("sort", "color"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sort может быть только parsedAt, price, title или publishedAt"));
    }

    @Test
    void wrongPriceReturns400() throws Exception {
        mockMvc.perform(get("/answer").param("minPrice", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Неверное значение параметра minPrice: abc"));
    }

    @Test
    void historyWithoutUrlReturns400() throws Exception {
        mockMvc.perform(get("/answer/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Не указан параметр url"));
    }

    @Test
    void unknownTaskReturns404() throws Exception {
        mockMvc.perform(get("/api/tasks/no-such-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Задача не найдена"));
    }

    @Test
    void wrongUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"not-a-link\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageWithoutPriceReturns422() throws Exception {
        when(pageLoader.load(anyString()))
                .thenReturn(new PageLoader.LoadedPage("<html><body><h1>О компании</h1></body></html>", null));

        mockMvc.perform(post("/api/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/about\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
