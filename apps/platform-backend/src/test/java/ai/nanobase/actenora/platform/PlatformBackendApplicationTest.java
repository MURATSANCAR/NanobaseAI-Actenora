package ai.nanobase.actenora.platform;

import ai.nanobase.actenora.identity.IdentityModule;
import ai.nanobase.actenora.testsupport.ModuleAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformBackendApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthExposesModules() throws Exception {
        ModuleAssertions.requireModuleName("identity", IdentityModule.name());
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.modules[0]").value("identity"));
    }
}
