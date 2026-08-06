package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic product/company ASR fixes for the AI path (FAZ-9 compatible surface rewrite).
 * Built-in Nanobase defaults; additional aliases can be supplied at construction.
 */
public final class MeetingTerminologyNormalizer {

    public record Alias(String surface, String canonical) {
    }

    private final List<Alias> aliases;

    /** Surface pattern compiled once at construction (not per rewrite call). */
    private record CompiledAlias(Pattern pattern, String canonical) {
    }

    private final List<CompiledAlias> compiled;

    public MeetingTerminologyNormalizer(List<Alias> aliases) {
        List<Alias> copy = new ArrayList<>(Objects.requireNonNull(aliases, "aliases"));
        copy.sort(Comparator
                .comparingInt((Alias a) -> a.surface().length())
                .reversed()
                .thenComparing(a -> a.surface().toLowerCase(Locale.ROOT)));
        this.aliases = List.copyOf(copy);
        List<CompiledAlias> compiledList = new ArrayList<>(copy.size());
        for (Alias al : copy) {
            compiledList.add(new CompiledAlias(
                    Pattern.compile(
                            "(?<![\\p{L}\\p{N}_])" + Pattern.quote(al.surface()) + "(?![\\p{L}\\p{N}_])",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    al.canonical()));
        }
        this.compiled = List.copyOf(compiledList);
    }

    /**
     * Multi-sector deterministic ASR/term glossary (finance, IT/AI, insurance, telecom, general).
     * Only high-confidence surface forms — uncommon misspellings, spelled-out acronyms and
     * casing fixes — so no common Turkish word is ever rewritten. Grow per sector as needed.
     */
    public static MeetingTerminologyNormalizer productionDefaults() {
        List<Alias> a = new ArrayList<>();

        // ---- Veritabanı ----
        a.add(new Alias("Mayusque", "MySQL"));
        a.add(new Alias("Moyus gale", "MySQL"));
        a.add(new Alias("moyusque", "MySQL"));
        a.add(new Alias("Moyusque", "MySQL"));
        a.add(new Alias("moyus gale", "MySQL"));
        a.add(new Alias("poscree", "PostgreSQL"));
        a.add(new Alias("Poscree", "PostgreSQL"));
        a.add(new Alias("poscre", "PostgreSQL"));
        a.add(new Alias("Postgre", "PostgreSQL"));
        a.add(new Alias("postgres", "PostgreSQL"));

        // ---- Donanım / AI altyapı ----
        a.add(new Alias("g p u", "GPU"));
        a.add(new Alias("gpu", "GPU"));
        a.add(new Alias("n vidia", "NVIDIA"));
        a.add(new Alias("in vidia", "NVIDIA"));
        a.add(new Alias("invdiya", "NVIDIA"));
        a.add(new Alias("invidya", "NVIDIA"));
        a.add(new Alias("invdia", "NVIDIA"));
        a.add(new Alias("envidia", "NVIDIA"));
        a.add(new Alias("nivida", "NVIDIA"));
        a.add(new Alias("nvidia", "NVIDIA"));
        a.add(new Alias("c u d a", "CUDA"));
        a.add(new Alias("cuda", "CUDA"));
        a.add(new Alias("h 200", "H200"));
        a.add(new Alias("h200", "H200"));

        // ---- IT / Yazılım / AI ----
        a.add(new Alias("d w h", "DWH"));
        a.add(new Alias("dwh", "DWH"));
        a.add(new Alias("d vaş", "DWH"));
        a.add(new Alias("devaaş", "DWH"));
        a.add(new Alias("e t l", "ETL"));
        a.add(new Alias("l l m", "LLM"));
        a.add(new Alias("gpt", "GPT"));
        a.add(new Alias("çet gpt", "GPT"));
        a.add(new Alias("chat gpt", "GPT"));
        a.add(new Alias("çet cpt", "GPT"));
        a.add(new Alias("cet gpt", "GPT"));
        a.add(new Alias("kubernates", "Kubernetes"));
        a.add(new Alias("kubernetis", "Kubernetes"));
        a.add(new Alias("k8s", "Kubernetes"));
        a.add(new Alias("on prem", "on-prem"));
        a.add(new Alias("on premise", "on-prem"));
        a.add(new Alias("on premises", "on-prem"));
        a.add(new Alias("onprem", "on-prem"));
        a.add(new Alias("10 prem", "on-prem"));
        a.add(new Alias("p o c", "PoC"));
        a.add(new Alias("proof of concept", "PoC"));
        a.add(new Alias("poc", "PoC"));

        // ---- Güvenlik ----
        a.add(new Alias("s i e m", "SIEM"));
        a.add(new Alias("si em", "SIEM"));
        a.add(new Alias("siem", "SIEM"));
        a.add(new Alias("k y c", "KYC"));

        // ---- Finans / Bankacılık ----
        a.add(new Alias("kor banking", "Core Banking"));
        a.add(new Alias("core bankin", "Core Banking"));
        a.add(new Alias("core bankacılık", "Core Banking"));
        a.add(new Alias("core banking", "Core Banking"));
        a.add(new Alias("corbanking", "Core Banking"));
        a.add(new Alias("corben king", "Core Banking"));
        a.add(new Alias("corbenk", "Core Banking"));
        a.add(new Alias("cor banking", "Core Banking"));
        a.add(new Alias("Fimple", "Simple"));
        a.add(new Alias("fimple", "Simple"));
        a.add(new Alias("Simpıl", "Simple"));
        a.add(new Alias("findeks", "Findeks"));
        a.add(new Alias("findex", "Findeks"));
        a.add(new Alias("b d d k", "BDDK"));
        a.add(new Alias("k v k k", "KVKK"));

        // ---- Telekom ----
        a.add(new Alias("g s m", "GSM"));

        addSectorTerms(a);
        addCompanies(a);
        return new MeetingTerminologyNormalizer(a);
    }

    /**
     * Broad multi-sector acronym/term set. Surface forms are safe because rewriting only
     * triggers on whole-word matches (Turkish suffixes like "API'ler" are preserved, and
     * "api" never touches "apiler"). Casing-only canonicals are harmless when already correct.
     */
    private static void addSectorTerms(List<Alias> a) {
        // Genel IT / Yazılım (kısaltma → doğru yazım)
        for (String s : List.of("api", "rest", "grpc", "graphql", "sdk", "ide", "sql", "nosql",
                "json", "xml", "yaml", "html", "css", "http", "https", "tcp", "udp", "dns", "cdn",
                "vpn", "ssl", "tls", "ssh", "url", "uri", "ui", "ux", "cli", "orm", "crud",
                "saas", "paas", "iaas", "mvp", "sla", "kpi", "roi", "okr", "rfp", "rfq", "nda",
                "b2b", "b2c", "erp", "crm", "hris", "cms", "dms")) {
            a.add(new Alias(s, s.toUpperCase(java.util.Locale.ROOT)));
        }
        a.add(new Alias("saas", "SaaS"));
        a.add(new Alias("paas", "PaaS"));
        a.add(new Alias("iaas", "IaaS"));
        a.add(new Alias("devops", "DevOps"));
        a.add(new Alias("dev ops", "DevOps"));
        a.add(new Alias("mlops", "MLOps"));
        a.add(new Alias("ci cd", "CI/CD"));
        a.add(new Alias("cicd", "CI/CD"));
        a.add(new Alias("microservice", "microservice"));

        // Bulut / DevOps / veri altyapı
        a.add(new Alias("aws", "AWS"));
        a.add(new Alias("azure", "Azure"));
        a.add(new Alias("gcp", "GCP"));
        a.add(new Alias("terraform", "Terraform"));
        a.add(new Alias("ansible", "Ansible"));
        a.add(new Alias("jenkins", "Jenkins"));
        a.add(new Alias("nginx", "Nginx"));
        a.add(new Alias("rabbitmq", "RabbitMQ"));
        a.add(new Alias("elasticsearch", "Elasticsearch"));
        a.add(new Alias("grafana", "Grafana"));
        a.add(new Alias("prometheus", "Prometheus"));
        a.add(new Alias("spark", "Spark"));
        a.add(new Alias("hadoop", "Hadoop"));
        a.add(new Alias("airflow", "Airflow"));
        a.add(new Alias("snowflake", "Snowflake"));
        a.add(new Alias("databricks", "Databricks"));

        // Yapay zeka / veri bilimi
        a.add(new Alias("nlp", "NLP"));
        a.add(new Alias("ocr", "OCR"));
        a.add(new Alias("iot", "IoT"));
        a.add(new Alias("rpa", "RPA"));
        a.add(new Alias("mcp", "MCP"));
        a.add(new Alias("pytorch", "PyTorch"));
        a.add(new Alias("tensorflow", "TensorFlow"));
        a.add(new Alias("hugging face", "Hugging Face"));
        a.add(new Alias("open ai", "OpenAI"));
        a.add(new Alias("chatgpt", "ChatGPT"));
        a.add(new Alias("embedding", "embedding"));
        a.add(new Alias("fine tuning", "fine-tuning"));
        a.add(new Alias("fine-tuning", "fine-tuning"));

        // Güvenlik
        for (String s : List.of("iam", "mfa", "sso", "rbac", "pki", "hsm", "waf", "ddos",
                "soc", "dlp", "edr", "xdr", "vpn", "pam", "casb")) {
            a.add(new Alias(s, s.toUpperCase(java.util.Locale.ROOT)));
        }
        a.add(new Alias("2 fa", "2FA"));
        a.add(new Alias("pen test", "pentest"));

        // Finans / Bankacılık
        for (String s : List.of("spk", "tcmb", "eft", "iban", "aml", "npl", "roa", "roe",
                "kkb", "masak", "mkk", "vkn", "tckn", "atm", "otp", "ftp", "gsyih")) {
            a.add(new Alias(s, s.toUpperCase(java.util.Locale.ROOT)));
        }
        a.add(new Alias("faast", "FAST"));
        a.add(new Alias("basel", "Basel"));
        a.add(new Alias("swift kodu", "SWIFT kodu"));
        a.add(new Alias("mobil bankac", "mobil bankacılık")); // guarded by word boundary on suffix

        // Sigorta
        a.add(new Alias("reasürans", "reasürans"));
        a.add(new Alias("re asürans", "reasürans"));
        a.add(new Alias("aktüerya", "aktüerya"));
        a.add(new Alias("bes", "BES"));

        // Hukuk / Uyum
        a.add(new Alias("gdpr", "GDPR"));
        a.add(new Alias("kvkk", "KVKK"));
        a.add(new Alias("iso 27001", "ISO 27001"));

        // Sağlık / Enerji / Perakende / Otomotiv (genel kısaltmalar)
        a.add(new Alias("hbys", "HBYS"));
        a.add(new Alias("epdk", "EPDK"));
        a.add(new Alias("scada", "SCADA"));
        a.add(new Alias("plc", "PLC"));
        a.add(new Alias("wms", "WMS"));
        a.add(new Alias("pim", "PIM"));
        a.add(new Alias("can bus", "CAN bus"));
    }

    /**
     * Popüler firma adları (Türkiye + global). Yalnızca yaygın Türkçe/İngilizce sözcüklerle
     * ÇAKIŞMAYAN, ayırt edici adlar — "Getir/Meta/Apple/İş/Ziraat" gibi kelime-firma çakışmaları
     * kasıtlı olarak dışarıda; onlar per-tenant sözlükten eklenmeli.
     */
    private static void addCompanies(List<Alias> a) {
        // --- Türkiye: bankalar / finans ---
        a.add(new Alias("akbank", "Akbank"));
        a.add(new Alias("garanti bbva", "Garanti BBVA"));
        a.add(new Alias("yapı kredi", "Yapı Kredi"));
        a.add(new Alias("yapıkredi", "Yapı Kredi"));
        a.add(new Alias("vakıfbank", "VakıfBank"));
        a.add(new Alias("halkbank", "Halkbank"));
        a.add(new Alias("ziraat bankası", "Ziraat Bankası"));
        a.add(new Alias("qnb finansbank", "QNB Finansbank"));
        a.add(new Alias("finansbank", "QNB Finansbank"));
        a.add(new Alias("denizbank", "DenizBank"));
        a.add(new Alias("burgan bank", "Burgan Bank"));
        a.add(new Alias("papara", "Papara"));
        a.add(new Alias("enpara", "Enpara"));
        a.add(new Alias("iyzico", "iyzico"));

        // --- Türkiye: telekom / holding / teknoloji ---
        a.add(new Alias("turkcell", "Turkcell"));
        a.add(new Alias("türk telekom", "Türk Telekom"));
        a.add(new Alias("turk telekom", "Türk Telekom"));
        a.add(new Alias("koç holding", "Koç Holding"));
        a.add(new Alias("sabancı holding", "Sabancı Holding"));
        a.add(new Alias("arçelik", "Arçelik"));
        a.add(new Alias("aselsan", "ASELSAN"));
        a.add(new Alias("havelsan", "HAVELSAN"));
        a.add(new Alias("togg", "TOGG"));
        a.add(new Alias("trendyol", "Trendyol"));
        a.add(new Alias("hepsiburada", "Hepsiburada"));
        a.add(new Alias("logo yazılım", "Logo Yazılım"));

        // --- Global: teknoloji ---
        a.add(new Alias("google", "Google"));
        a.add(new Alias("microsoft", "Microsoft"));
        a.add(new Alias("maykrosoft", "Microsoft"));
        a.add(new Alias("amazon", "Amazon"));
        a.add(new Alias("oracle", "Oracle"));
        a.add(new Alias("salesforce", "Salesforce"));
        a.add(new Alias("adobe", "Adobe"));
        a.add(new Alias("atlassian", "Atlassian"));
        a.add(new Alias("vmware", "VMware"));
        a.add(new Alias("cisco", "Cisco"));
        a.add(new Alias("intel", "Intel"));
        a.add(new Alias("openai", "OpenAI"));
        a.add(new Alias("anthropic", "Anthropic"));
        a.add(new Alias("deepmind", "DeepMind"));
        a.add(new Alias("huggingface", "Hugging Face"));

        // --- Global: finans / ödeme ---
        a.add(new Alias("mastercard", "Mastercard"));
        a.add(new Alias("paypal", "PayPal"));
        a.add(new Alias("stripe", "Stripe"));
    }

    public String rewrite(String text) {
        if (text == null || text.isEmpty() || compiled.isEmpty()) {
            return text == null ? "" : text;
        }
        String result = text;
        for (CompiledAlias alias : compiled) {
            Matcher matcher = alias.pattern().matcher(result);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                if (!matcher.group().equals(alias.canonical())) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(alias.canonical()));
                    changed = true;
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                }
            }
            matcher.appendTail(sb);
            if (changed) {
                result = sb.toString();
            }
        }
        return result;
    }
}
