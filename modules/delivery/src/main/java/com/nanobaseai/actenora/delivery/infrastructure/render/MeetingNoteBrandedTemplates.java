package com.nanobaseai.actenora.delivery.infrastructure.render;

import com.nanobaseai.actenora.delivery.application.model.DraftMinutesReadyMailBody;
import com.nanobaseai.actenora.delivery.application.model.MeetingEndedMailBody;
import com.nanobaseai.actenora.delivery.application.model.MeetingNoteDocument;
import com.nanobaseai.actenora.delivery.application.model.MeetingNoteParticipant;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Nanobase-branded HTML for meeting note email bodies and PDF attachments.
 */
public final class MeetingNoteBrandedTemplates {

    private MeetingNoteBrandedTemplates() {
    }

    public static String emailHtml(MeetingNoteDocument doc) {
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Toplantı Notu</title>
                </head>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f1f5f9;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 12px 40px rgba(15,23,42,0.12);">
                        %s
                        <tr><td style="padding:32px 36px 8px 36px;">
                          <p style="margin:0 0 8px 0;font-size:12px;font-weight:600;letter-spacing:0.08em;text-transform:uppercase;color:#0d9488;">Toplantı Notu</p>
                          <h1 style="margin:0 0 12px 0;font-size:26px;line-height:1.25;color:#1e1b4b;">%s</h1>
                          <p style="margin:0;font-size:14px;color:#64748b;">%s · %s · %s</p>
                        </td></tr>
                        <tr><td style="padding:24px 36px;">
                          <div style="background:linear-gradient(135deg,#f5f3ff 0%%,#ecfeff 100%%);border-radius:12px;padding:20px 22px;border:1px solid #e9d5ff;">
                            <p style="margin:0 0 8px 0;font-size:12px;font-weight:700;color:#6d28d9;text-transform:uppercase;letter-spacing:0.06em;">Yönetici Özeti</p>
                            <p style="margin:0;font-size:15px;line-height:1.65;color:#334155;white-space:pre-wrap;">%s</p>
                          </div>
                        </td></tr>
                        %s
                        %s
                        <tr><td style="padding:8px 36px 28px 36px;">
                          <p style="margin:0;font-size:13px;line-height:1.6;color:#64748b;">
                            Detaylı tutanak ve karar listesi <strong>PDF ekinde</strong> yer almaktadır.
                            Bu ileti <strong>Nanobase Actenora</strong> tarafından otomatik oluşturulmuştur.
                          </p>
                        </td></tr>
                        %s
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                emailHeader(),
                escape(doc.meetingTitle()),
                escape(doc.meetingDate()),
                escape(doc.duration()),
                escape(doc.organizer()),
                escapeMultiline(doc.executiveSummary()),
                emailListSection("Kararlar", doc.decisions(), "#7c3aed"),
                emailListSection("Aksiyonlar", doc.actions(), "#0d9488"),
                emailFooter()
        );
    }

    public static String emailHtmlFromPlain(String subject, String bodyText, String portalUrl) {
        MeetingNoteDocument doc = new MeetingNoteDocument(
                subject == null || subject.isBlank() ? "Toplantı Notu" : subject,
                "",
                "",
                "",
                List.of(),
                bodyText == null ? "" : bodyText,
                List.of(),
                List.of(),
                "Actenora · NanobaseAI"
        );
        String html = emailHtml(doc);
        if (portalUrl != null && !portalUrl.isBlank()) {
            html = html.replace(
                    "PDF ekinde",
                    "<a href=\"" + escapeAttr(portalUrl) + "\" style=\"color:#6d28d9;\">portal bağlantısı</a> ve PDF ekinde"
            );
        }
        return html;
    }

    /** Organizer status mail when a meeting ends and note generation starts. */
    public static String meetingEndedEmailHtml(MeetingEndedMailBody body) {
        String whenRow = body.whenLabel() == null || body.whenLabel().isBlank()
                ? ""
                : """
                  <tr>
                    <td style="padding:6px 0;color:#64748b;">Tarih</td>
                    <td style="padding:6px 0;">%s</td>
                  </tr>
                  """.formatted(escape(body.whenLabel()));
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Toplantınız bitti</title>
                </head>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f1f5f9;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 12px 40px rgba(15,23,42,0.12);">
                        %s
                        <tr><td style="padding:32px 36px 8px 36px;">
                          <p style="margin:0 0 8px 0;font-size:12px;font-weight:600;letter-spacing:0.08em;text-transform:uppercase;color:#0d9488;">Durum bildirimi</p>
                          <h1 style="margin:0 0 14px 0;font-size:24px;line-height:1.3;color:#1e1b4b;">Toplantınız sona erdi</h1>
                          <p style="margin:0;font-size:16px;line-height:1.65;color:#334155;">
                            <strong style="color:#1e1b4b;">%s</strong> toplantısı tamamlandı.
                            <strong>NanobaseAI EasyMeeting</strong> toplantı notu oluşturma işlemlerine başladı.
                            İşlemler tamamlandığında size tekrar haber vereceğiz.
                          </p>
                        </td></tr>
                        <tr><td style="padding:24px 36px;">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:linear-gradient(135deg,#f5f3ff 0%%,#ecfeff 100%%);border-radius:12px;border:1px solid #e9d5ff;">
                            <tr><td style="padding:20px 22px;">
                              <p style="margin:0 0 12px 0;font-size:12px;font-weight:700;color:#6d28d9;text-transform:uppercase;letter-spacing:0.06em;">Toplantı özeti</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="font-size:14px;color:#334155;">
                                <tr>
                                  <td style="padding:6px 0;width:120px;color:#64748b;">Başlık</td>
                                  <td style="padding:6px 0;font-weight:600;">%s</td>
                                </tr>
                                %s
                                <tr>
                                  <td style="padding:6px 0;color:#64748b;">Organizatör</td>
                                  <td style="padding:6px 0;">Siz</td>
                                </tr>
                              </table>
                            </td></tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:0 36px 8px 36px;">
                          <p style="margin:0 0 14px 0;font-size:12px;font-weight:700;color:#7c3aed;text-transform:uppercase;letter-spacing:0.06em;">Şu anda ne oluyor?</p>
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                            <tr>
                              <td style="width:36px;vertical-align:top;padding-bottom:14px;">
                                <div style="width:28px;height:28px;border-radius:50%%;background:#0d9488;color:#fff;text-align:center;line-height:28px;font-size:13px;font-weight:700;">✓</div>
                              </td>
                              <td style="vertical-align:top;padding-bottom:14px;padding-top:4px;">
                                <div style="font-size:14px;font-weight:600;color:#0f766e;">Toplantı bitti</div>
                                <div style="font-size:13px;color:#64748b;">Kayıt ve konuşma verisi alındı</div>
                              </td>
                            </tr>
                            <tr>
                              <td style="width:36px;vertical-align:top;padding-bottom:14px;">
                                <div style="width:28px;height:28px;border-radius:50%%;background:#7c3aed;color:#fff;text-align:center;line-height:28px;font-size:12px;font-weight:700;">2</div>
                              </td>
                              <td style="vertical-align:top;padding-bottom:14px;padding-top:4px;">
                                <div style="font-size:14px;font-weight:600;color:#5b21b6;">Not oluşturuluyor</div>
                                <div style="font-size:13px;color:#64748b;">NanobaseAI EasyMeeting analiz ve tutanak üretiyor</div>
                              </td>
                            </tr>
                            <tr>
                              <td style="width:36px;vertical-align:top;">
                                <div style="width:28px;height:28px;border-radius:50%%;background:#e2e8f0;color:#64748b;text-align:center;line-height:28px;font-size:12px;font-weight:700;">3</div>
                              </td>
                              <td style="vertical-align:top;padding-top:4px;">
                                <div style="font-size:14px;font-weight:600;color:#475569;">Size haber vereceğiz</div>
                                <div style="font-size:13px;color:#64748b;">Taslak hazır olduğunda e-posta alacaksınız</div>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:28px 36px 8px 36px;" align="center">
                          <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#6d28d9 0%%,#0f766e 100%%);color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;padding:14px 28px;border-radius:10px;">
                            Toplantıya git
                          </a>
                        </td></tr>
                        <tr><td style="padding:16px 36px 28px 36px;">
                          <p style="margin:0;font-size:13px;line-height:1.6;color:#64748b;text-align:center;">
                            Bu ileti otomatik gönderilmiştir. Yanıtlamanız gerekmez.
                            Tamamlandığında ayrı bir e-posta ile bilgilendirileceksiniz.
                          </p>
                        </td></tr>
                        %s
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                meetingEndedHeader(),
                escape(body.meetingTitle()),
                escape(body.meetingTitle()),
                whenRow,
                escapeAttr(body.meetingUrl()),
                easyMeetingFooter()
        );
    }

    /** Organizer mail when draft minutes are ready and awaiting approval. */
    public static String draftMinutesReadyEmailHtml(DraftMinutesReadyMailBody body) {
        String whenRow = body.whenLabel() == null || body.whenLabel().isBlank()
                ? ""
                : """
                  <tr>
                    <td style="padding:6px 0;color:#64748b;">Tarih</td>
                    <td style="padding:6px 0;">%s</td>
                  </tr>
                  """.formatted(escape(body.whenLabel()));
        String summaryBlock = body.executiveSummary() == null || body.executiveSummary().isBlank()
                ? ""
                : """
                  <tr><td style="padding:0 36px 8px 36px;">
                    <div style="background:#f8fafc;border-radius:12px;padding:18px 20px;border:1px solid #e2e8f0;">
                      <p style="margin:0 0 8px 0;font-size:12px;font-weight:700;color:#0d9488;text-transform:uppercase;letter-spacing:0.06em;">Yönetici özeti</p>
                      <p style="margin:0;font-size:14px;line-height:1.65;color:#334155;">%s</p>
                    </div>
                  </td></tr>
                  """.formatted(escape(body.executiveSummary()));
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Tutanak hazır</title>
                </head>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f1f5f9;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 12px 40px rgba(15,23,42,0.12);">
                        %s
                        <tr><td style="padding:32px 36px 8px 36px;">
                          <p style="margin:0 0 8px 0;font-size:12px;font-weight:600;letter-spacing:0.08em;text-transform:uppercase;color:#0d9488;">Durum bildirimi</p>
                          <h1 style="margin:0 0 14px 0;font-size:24px;line-height:1.3;color:#1e1b4b;">Toplantı tutanağı hazır</h1>
                          <p style="margin:0;font-size:16px;line-height:1.65;color:#334155;">
                            <strong style="color:#1e1b4b;">%s</strong> toplantısı için tutanak hazırlandı
                            ve <strong>sizin onayınızı bekliyor</strong>.
                          </p>
                        </td></tr>
                        <tr><td style="padding:24px 36px;">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:linear-gradient(135deg,#f5f3ff 0%%,#ecfeff 100%%);border-radius:12px;border:1px solid #e9d5ff;">
                            <tr><td style="padding:20px 22px;">
                              <p style="margin:0 0 12px 0;font-size:12px;font-weight:700;color:#6d28d9;text-transform:uppercase;letter-spacing:0.06em;">Toplantı özeti</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="font-size:14px;color:#334155;">
                                <tr>
                                  <td style="padding:6px 0;width:120px;color:#64748b;">Başlık</td>
                                  <td style="padding:6px 0;font-weight:600;">%s</td>
                                </tr>
                                %s
                                <tr>
                                  <td style="padding:6px 0;color:#64748b;">Durum</td>
                                  <td style="padding:6px 0;font-weight:600;color:#6d28d9;">Onay bekliyor</td>
                                </tr>
                              </table>
                            </td></tr>
                          </table>
                        </td></tr>
                        %s
                        <tr><td style="padding:8px 36px 8px 36px;">
                          <p style="margin:0 0 14px 0;font-size:12px;font-weight:700;color:#7c3aed;text-transform:uppercase;letter-spacing:0.06em;">Sıradaki adım</p>
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                            <tr>
                              <td style="width:36px;vertical-align:top;padding-bottom:14px;">
                                <div style="width:28px;height:28px;border-radius:50%%;background:#0d9488;color:#fff;text-align:center;line-height:28px;font-size:13px;font-weight:700;">✓</div>
                              </td>
                              <td style="vertical-align:top;padding-bottom:14px;padding-top:4px;">
                                <div style="font-size:14px;font-weight:600;color:#0f766e;">Tutanak hazır</div>
                                <div style="font-size:13px;color:#64748b;">NanobaseAI EasyMeeting taslağı oluşturdu</div>
                              </td>
                            </tr>
                            <tr>
                              <td style="width:36px;vertical-align:top;padding-bottom:14px;">
                                <div style="width:28px;height:28px;border-radius:50%%;background:#7c3aed;color:#fff;text-align:center;line-height:28px;font-size:12px;font-weight:700;">2</div>
                              </td>
                              <td style="vertical-align:top;padding-bottom:14px;padding-top:4px;">
                                <div style="font-size:14px;font-weight:600;color:#5b21b6;">Onayınız bekleniyor</div>
                                <div style="font-size:13px;color:#64748b;">İnceleyip onaylayın veya düzenleyin</div>
                              </td>
                            </tr>
                            <tr>
                              <td style="width:36px;vertical-align:top;">
                                <div style="width:28px;height:28px;border-radius:50%%;background:#e2e8f0;color:#64748b;text-align:center;line-height:28px;font-size:12px;font-weight:700;">3</div>
                              </td>
                              <td style="vertical-align:top;padding-top:4px;">
                                <div style="font-size:14px;font-weight:600;color:#475569;">Dağıtım</div>
                                <div style="font-size:13px;color:#64748b;">Onay sonrası nihai tutanak gönderilir</div>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:28px 36px 8px 36px;" align="center">
                          <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#6d28d9 0%%,#0f766e 100%%);color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;padding:14px 28px;border-radius:10px;">
                            Tutanaga git
                          </a>
                        </td></tr>
                        <tr><td style="padding:16px 36px 28px 36px;">
                          <p style="margin:0;font-size:13px;line-height:1.6;color:#64748b;text-align:center;">
                            Bu ileti otomatik gönderilmiştir. Onay için portal üzerinden ilerleyin.
                          </p>
                        </td></tr>
                        %s
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                draftMinutesReadyHeader(),
                escape(body.meetingTitle()),
                escape(body.meetingTitle()),
                whenRow,
                summaryBlock,
                escapeAttr(body.meetingUrl()),
                easyMeetingFooter()
        );
    }

    public static String pdfHtml(MeetingNoteDocument doc) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="tr">
                <head>
                  <meta charset="UTF-8" />
                  <style>
                    @page { size: A4; margin: 14mm 16mm; }
                    body { font-family: sans-serif; font-size: 10.5pt; color: #1e293b; line-height: 1.5; }
                    .header { background: linear-gradient(135deg, #4c1d95 0%%, #0f766e 100%%); color: white; padding: 22px 24px; border-radius: 12px; margin-bottom: 18px; }
                    .brand { font-size: 11pt; letter-spacing: 0.14em; text-transform: uppercase; opacity: 0.92; margin-bottom: 6px; }
                    h1 { font-size: 22pt; margin: 0 0 8px 0; line-height: 1.2; }
                    .meta { font-size: 10pt; opacity: 0.92; }
                    .badge { display: inline-block; background: rgba(255,255,255,0.18); padding: 4px 10px; border-radius: 999px; margin-right: 8px; }
                    .section { margin: 18px 0 14px 0; page-break-inside: avoid; }
                    .section-title { font-size: 11pt; font-weight: bold; color: #5b21b6; text-transform: uppercase; letter-spacing: 0.08em; border-bottom: 2px solid #ddd6fe; padding-bottom: 6px; margin-bottom: 10px; }
                    .summary { background: #f5f3ff; border-left: 4px solid #7c3aed; padding: 14px 16px; border-radius: 0 8px 8px 0; white-space: pre-wrap; }
                    ul { margin: 8px 0 0 18px; padding: 0; }
                    li { margin-bottom: 7px; }
                    table.participants { width: 100%%; border-collapse: collapse; margin-top: 8px; }
                    table.participants th, table.participants td { border: 1px solid #e2e8f0; padding: 8px 10px; text-align: left; }
                    table.participants th { background: #f8fafc; color: #475569; font-size: 9pt; text-transform: uppercase; letter-spacing: 0.06em; }
                    .footer { margin-top: 28px; padding-top: 12px; border-top: 1px solid #e2e8f0; font-size: 9pt; color: #64748b; text-align: center; }
                  </style>
                </head>
                <body>
                  <div class="header">
                    <div class="brand">Nanobase · Actenora</div>
                    <h1>%s</h1>
                    <div class="meta">
                      <span class="badge">%s</span>
                      <span class="badge">%s</span>
                      <span class="badge">Organizer: %s</span>
                    </div>
                  </div>
                  <div class="section">
                    <div class="section-title">Yönetici Özeti</div>
                    <div class="summary">%s</div>
                  </div>
                  %s
                  %s
                  %s
                  <div class="footer">%s · portal.nanobase.ai/easymeeting · Gizli / Kurum İçi</div>
                </body>
                </html>
                """.formatted(
                escape(doc.meetingTitle()),
                escape(doc.meetingDate()),
                escape(doc.duration()),
                escape(doc.organizer()),
                escapeMultiline(doc.executiveSummary()),
                pdfParticipants(doc.participants()),
                pdfListSection("Kararlar", doc.decisions()),
                pdfListSection("Aksiyonlar", doc.actions()),
                escape(doc.generatedBy())
        );
    }

    private static String emailHeader() {
        return """
                <tr><td style="background:linear-gradient(135deg,#4c1d95 0%%,#0f766e 100%%);padding:28px 36px;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr>
                    <td>
                      <div style="font-size:13px;font-weight:700;letter-spacing:0.16em;text-transform:uppercase;color:rgba(255,255,255,0.92);">Nanobase</div>
                      <div style="font-size:22px;font-weight:700;color:#ffffff;margin-top:4px;">Actenora Intelligence</div>
                    </td>
                    <td align="right" style="vertical-align:middle;">
                      <div style="width:52px;height:52px;border-radius:14px;background:rgba(255,255,255,0.16);display:inline-block;text-align:center;line-height:52px;font-size:24px;color:#fff;">◆</div>
                    </td>
                  </tr></table>
                </td></tr>
                """;
    }

    private static String emailFooter() {
        return """
                <tr><td style="background:#f8fafc;padding:18px 36px;border-top:1px solid #e2e8f0;">
                  <p style="margin:0;font-size:12px;color:#94a3b8;text-align:center;">
                    © Nanobase · <a href="https://portal.nanobase.ai/easymeeting/" style="color:#6d28d9;text-decoration:none;">portal.nanobase.ai</a>
                  </p>
                </td></tr>
                """;
    }

    private static String meetingEndedHeader() {
        return """
                <tr><td style="background:linear-gradient(135deg,#4c1d95 0%%,#0f766e 100%%);padding:28px 36px;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr>
                    <td>
                      <div style="font-size:12px;font-weight:700;letter-spacing:0.16em;text-transform:uppercase;color:rgba(255,255,255,0.88);">NanobaseAI</div>
                      <div style="font-size:22px;font-weight:700;color:#ffffff;margin-top:4px;">EasyMeeting</div>
                    </td>
                    <td align="right" style="vertical-align:middle;">
                      <div style="display:inline-block;padding:8px 14px;border-radius:999px;background:rgba(255,255,255,0.16);font-size:12px;font-weight:600;color:#fff;letter-spacing:0.04em;">Toplantı bitti</div>
                    </td>
                  </tr></table>
                </td></tr>
                """;
    }

    private static String draftMinutesReadyHeader() {
        return """
                <tr><td style="background:linear-gradient(135deg,#4c1d95 0%%,#0f766e 100%%);padding:28px 36px;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr>
                    <td>
                      <div style="font-size:12px;font-weight:700;letter-spacing:0.16em;text-transform:uppercase;color:rgba(255,255,255,0.88);">NanobaseAI</div>
                      <div style="font-size:22px;font-weight:700;color:#ffffff;margin-top:4px;">EasyMeeting</div>
                    </td>
                    <td align="right" style="vertical-align:middle;">
                      <div style="display:inline-block;padding:8px 14px;border-radius:999px;background:rgba(255,255,255,0.16);font-size:12px;font-weight:600;color:#fff;letter-spacing:0.04em;">Onay bekliyor</div>
                    </td>
                  </tr></table>
                </td></tr>
                """;
    }

    private static String easyMeetingFooter() {
        return """
                <tr><td style="background:#f8fafc;padding:18px 36px;border-top:1px solid #e2e8f0;">
                  <p style="margin:0;font-size:12px;color:#94a3b8;text-align:center;">
                    © NanobaseAI EasyMeeting · <a href="https://www.nanobase.ai" style="color:#6d28d9;text-decoration:none;">www.nanobase.ai</a>
                  </p>
                </td></tr>
                """;
    }

    private static String emailListSection(String title, List<String> items, String accent) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        String lis = items.stream()
                .map(i -> "<li style=\"margin-bottom:8px;font-size:14px;line-height:1.55;color:#334155;\">"
                        + escape(i) + "</li>")
                .collect(Collectors.joining());
        return """
                <tr><td style="padding:0 36px 16px 36px;">
                  <p style="margin:0 0 10px 0;font-size:12px;font-weight:700;color:%s;text-transform:uppercase;letter-spacing:0.06em;">%s</p>
                  <ul style="margin:0;padding-left:20px;">%s</ul>
                </td></tr>
                """.formatted(accent, escape(title), lis);
    }

    private static String pdfListSection(String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        String lis = items.stream().map(i -> "<li>" + escape(i) + "</li>").collect(Collectors.joining());
        return """
                <div class="section">
                  <div class="section-title">%s</div>
                  <ul>%s</ul>
                </div>
                """.formatted(escape(title), lis);
    }

    private static String pdfParticipants(List<MeetingNoteParticipant> participants) {
        if (participants == null || participants.isEmpty()) {
            return "";
        }
        String rows = participants.stream()
                .map(p -> "<tr><td>" + escape(p.name())
                        + (p.email().isBlank() ? "" : "<div style=\"font-size:8pt;color:#64748b;\">" + escape(p.email()) + "</div>")
                        + "</td><td>" + escape(blankOr(p.role(), "Katılımcı"))
                        + "</td><td>" + escape(blankOr(p.attendance(), "—"))
                        + "</td></tr>")
                .collect(Collectors.joining());
        return """
                <div class="section">
                  <div class="section-title">Katılımcılar</div>
                  <table class="participants">
                    <thead><tr><th>Ad</th><th>Rol</th><th>Katılım</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </div>
                """.formatted(rows);
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Escapes HTML and preserves line breaks for scannable summaries. */
    private static String escapeMultiline(String value) {
        return escape(value).replace("\r\n", "\n").replace("\n", "<br/>");
    }

    private static String escapeAttr(String value) {
        return escape(value).replace("'", "&#39;");
    }
}
