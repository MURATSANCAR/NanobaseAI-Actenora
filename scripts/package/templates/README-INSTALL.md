# EasyMeeting — Kurulum (Installation)

Bu paket, EasyMeeting'i tek bir sunucuda Docker ile çalıştırır. Kaynak kod
derlemesi **gerekmez** — sadece Docker Engine + compose eklentisi yeterli.
LLM (dil modeli) bu pakete **dahil değildir**; kendi OpenAI-uyumlu uç noktanızı
tanımlarsınız.

## Gereksinimler
- Docker Engine 24+ ve `docker compose` eklentisi
- ~4 CPU / 8 GB RAM (LLM hariç), ~20 GB disk
- Erişilebilir bir OpenAI-uyumlu **LLM** ve **embedding** uç noktası
- (Dış erişim için) önünde nginx + TLS

## Adımlar

### 1) Paketi açın
```bash
tar -xzf actenora-bundle-<tag>.tar.gz -C easymeeting/
cd easymeeting/
```

### 2) Ortam dosyasını doldurun
```bash
cp actenora.env.example actenora.env
chmod 600 actenora.env
# actenora.env içindeki tüm (REQUIRED) alanları doldurun.
```
`change_me` / `actenora_local` gibi örnek değerler kalırsa kurulum durur.

**Sertifika (Graph CERTIFICATE modu):** `compose/secrets/graph/` altına
`cert.pem` ve `key.pem` koyun (0600).

### 3) Kurun ve başlatın
```bash
./install.sh
# AI sidecar da isteniyorsa:
./install.sh --profile ai
```
`install.sh` imajları `docker load` ile yükler, yapılandırmayı doğrular ve
stack'i başlatır. İlk açılışta backend ~2 dk sürebilir (DB migrasyonları).

### 4) Doğrulayın
```bash
./install.sh ps
./install.sh logs
curl -fsS http://127.0.0.1:8088/actuator/health | grep UP
```

### 5) LLM / embedding bağlantı testi
Portal açıldıktan sonra **Ayarlar → Bağlantılar** ekranından LLM ve embedding
uç noktalarını girip **Test Et** ile doğrulayabilirsiniz.

## Yönetim
```bash
./install.sh ps       # durum
./install.sh logs     # loglar
./install.sh down     # durdur (veriyi SİLMEZ; named volume'larda kalır)
```

## Güncelleme
Yeni bir `actenora-bundle-<yeni-tag>.tar.gz` alın, açın, `actenora.env`
içindeki `ACTENORA_IMAGE_TAG` değerini güncelleyin, `./install.sh` çalıştırın.

## Notlar
- Veri servisleri (postgres/redis/rabbitmq/minio) yalnızca iç ağdadır; dışarı
  açık değildir. Backend ve portal `127.0.0.1`'e bağlanır — önüne nginx/TLS koyun.
- Portal'ın Entra ayarları imaja derleme anında gömülür; değişirse imaj yeniden
  üretilmelidir (satıcı tarafında).
- Registry modu: `ACTENORA_IMAGE_REGISTRY` ayarlayıp `./install.sh --pull` kullanın.
