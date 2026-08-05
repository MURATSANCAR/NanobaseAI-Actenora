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
                    <td style="padding:11px 0;border-top:1px solid #eef2f7;color:#7c879b;font-size:13px;white-space:nowrap;">Tarih</td>
                    <td style="padding:11px 0 11px 16px;border-top:1px solid #eef2f7;color:#1f2a44;font-size:14px;font-weight:600;">%s</td>
                  </tr>
                  """.formatted(escape(body.whenLabel()));
        String summaryBlock = body.executiveSummary() == null || body.executiveSummary().isBlank()
                ? ""
                : """
                  <tr><td class="px" style="padding:4px 40px 8px 40px;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:separate;">
                      <tr>
                        <td width="4" style="width:4px;background:linear-gradient(180deg,#7c3aed 0%%,#0d9488 100%%);border-radius:4px;"></td>
                        <td style="padding-left:18px;">
                          <p style="margin:0 0 7px 0;font-size:11px;font-weight:700;color:#0d9488;text-transform:uppercase;letter-spacing:0.1em;">Yönetici özeti</p>
                          <p style="margin:0;font-size:15px;line-height:1.7;color:#41506b;">%s</p>
                        </td>
                      </tr>
                    </table>
                  </td></tr>
                  """.formatted(escape(body.executiveSummary()));
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <meta name="color-scheme" content="light" />
                  <title>Tutanak hazır</title>
                  <style>
                    @media only screen and (max-width: 620px) {
                      .card { width: 100%% !important; border-radius: 0 !important; }
                      .px { padding-left: 24px !important; padding-right: 24px !important; }
                      .hero { padding: 30px 24px !important; }
                      .h1 { font-size: 23px !important; }
                      .cta { display: block !important; }
                    }
                  </style>
                </head>
                <body style="margin:0;padding:0;background:#eef1f6;-webkit-font-smoothing:antialiased;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">Tutanak hazır — onayınızı bekliyor.</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#eef1f6;padding:40px 12px;">
                    <tr><td align="center">
                      <table role="presentation" class="card" width="620" cellspacing="0" cellpadding="0" style="max-width:620px;background:#ffffff;border-radius:20px;overflow:hidden;box-shadow:0 20px 60px rgba(20,28,50,0.14);">
                        %s
                        <tr><td class="px" style="padding:36px 40px 6px 40px;">
                          <table role="presentation" cellspacing="0" cellpadding="0"><tr><td style="background:#eefdf8;border:1px solid #b9f0df;border-radius:999px;padding:6px 14px;font-size:11px;font-weight:700;letter-spacing:0.09em;text-transform:uppercase;color:#0b7a68;">Tutanak hazır</td></tr></table>
                          <h1 class="h1" style="margin:18px 0 12px 0;font-size:27px;line-height:1.28;color:#131c33;letter-spacing:-0.4px;font-weight:700;">Toplantı tutanağınız<br />onayınızı bekliyor</h1>
                          <p style="margin:0;font-size:16px;line-height:1.7;color:#54617b;">
                            <strong style="color:#131c33;">%s</strong> toplantısının tutanağı hazırlandı.
                            Aşağıdaki özeti inceleyip onayladıktan sonra nihai sürüm katılımcılara dağıtılacak.
                          </p>
                        </td></tr>
                        <tr><td class="px" style="padding:26px 40px 20px 40px;">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#fafbfd;border:1px solid #e7ebf2;border-radius:14px;">
                            <tr><td style="padding:22px 24px;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td style="padding:0 0 11px 0;color:#7c879b;font-size:13px;white-space:nowrap;">Toplantı</td>
                                  <td style="padding:0 0 11px 16px;color:#1f2a44;font-size:14px;font-weight:600;">%s</td>
                                </tr>
                                %s
                                <tr>
                                  <td style="padding:11px 0 0 0;border-top:1px solid #eef2f7;color:#7c879b;font-size:13px;white-space:nowrap;">Durum</td>
                                  <td style="padding:11px 0 0 16px;border-top:1px solid #eef2f7;">
                                    <span style="display:inline-block;background:#f3edff;color:#6d28d9;border-radius:999px;padding:4px 12px;font-size:12px;font-weight:700;">Onay bekliyor</span>
                                  </td>
                                </tr>
                              </table>
                            </td></tr>
                          </table>
                        </td></tr>
                        %s
                        %s
                        %s
                        %s
                        %s
                        <tr><td class="px" style="padding:22px 40px 4px 40px;">
                          <p style="margin:0 0 16px 0;font-size:11px;font-weight:700;color:#7c3aed;text-transform:uppercase;letter-spacing:0.1em;">Süreç</p>
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                            <tr>
                              <td width="34" style="width:34px;vertical-align:top;">
                                <div style="width:26px;height:26px;border-radius:50%%;background:#0d9488;color:#ffffff;text-align:center;line-height:26px;font-size:13px;font-weight:700;">&#10003;</div>
                              </td>
                              <td style="vertical-align:top;padding:1px 0 16px 0;">
                                <div style="font-size:14px;font-weight:700;color:#0b7a68;">Tutanak oluşturuldu</div>
                                <div style="font-size:13px;line-height:1.55;color:#7c879b;">EasyMeeting toplantıdan taslak tutanağı üretti</div>
                              </td>
                            </tr>
                            <tr>
                              <td width="34" style="width:34px;vertical-align:top;">
                                <div style="width:26px;height:26px;border-radius:50%%;background:#7c3aed;color:#ffffff;text-align:center;line-height:26px;font-size:12px;font-weight:700;">2</div>
                              </td>
                              <td style="vertical-align:top;padding:1px 0 16px 0;">
                                <div style="font-size:14px;font-weight:700;color:#5b21b6;">Onayınız bekleniyor</div>
                                <div style="font-size:13px;line-height:1.55;color:#7c879b;">Portalde inceleyin, düzenleyin veya onaylayın</div>
                              </td>
                            </tr>
                            <tr>
                              <td width="34" style="width:34px;vertical-align:top;">
                                <div style="width:26px;height:26px;border-radius:50%%;background:#e7ebf2;color:#8b97ab;text-align:center;line-height:26px;font-size:12px;font-weight:700;">3</div>
                              </td>
                              <td style="vertical-align:top;padding:1px 0 0 0;">
                                <div style="font-size:14px;font-weight:700;color:#5b6779;">Dağıtım</div>
                                <div style="font-size:13px;line-height:1.55;color:#7c879b;">Onay sonrası nihai tutanak katılımcılara gönderilir</div>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td class="px" style="padding:30px 40px 10px 40px;" align="center">
                          <a class="cta" href="%s" style="display:inline-block;background:linear-gradient(135deg,#6d28d9 0%%,#0f766e 100%%);color:#ffffff;text-decoration:none;font-size:15px;font-weight:700;padding:16px 40px;border-radius:12px;box-shadow:0 10px 24px rgba(109,40,217,0.28);">Tutanağı incele ve onayla</a>
                        </td></tr>
                        <tr><td class="px" style="padding:6px 40px 30px 40px;" align="center">
                          <p style="margin:0;font-size:12px;line-height:1.6;color:#93a0b4;">
                            Buton çalışmıyorsa: <a href="%s" style="color:#6d28d9;text-decoration:underline;word-break:break-all;">%s</a>
                          </p>
                        </td></tr>
                        <tr><td class="px" style="padding:0 40px;"><div style="height:1px;background:#eef2f7;"></div></td></tr>
                        <tr><td class="px" style="padding:20px 40px 28px 40px;">
                          <p style="margin:0;font-size:12px;line-height:1.65;color:#93a0b4;text-align:center;">
                            Bu ileti NanobaseAI EasyMeeting tarafından otomatik gönderilmiştir. Yanıtlamanız gerekmez.
                          </p>
                        </td></tr>
                        %s
                      </table>
                      <table role="presentation" width="620" cellspacing="0" cellpadding="0" style="max-width:620px;">
                        <tr><td style="padding:18px 8px 0 8px;" align="center">
                          <p style="margin:0;font-size:11px;line-height:1.6;color:#9aa6ba;">Gizli / Kurum içi · Yalnızca ilgili katılımcılar için</p>
                        </td></tr>
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
                draftCountsRow(body),
                draftDecisionsSection(body.decisions()),
                draftActionsSection(body.actions()),
                draftQuestionsSection(body.openQuestions()),
                draftReviewNotice(body.reviewFlagCount()),
                escapeAttr(body.meetingUrl()),
                escapeAttr(body.meetingUrl()),
                escape(body.meetingUrl()),
                easyMeetingFooter()
        );
    }

    /** Scannable chips: how many decisions / actions / open questions the draft contains. */
    private static String draftCountsRow(DraftMinutesReadyMailBody body) {
        if (!body.hasContent()) {
            return "";
        }
        StringBuilder chips = new StringBuilder();
        chips.append(countChip(body.decisions().size(), "karar", "#f3edff", "#6d28d9"));
        chips.append(countChip(body.actions().size(), "aksiyon", "#eefdf8", "#0b7a68"));
        chips.append(countChip(body.openQuestions().size(), "açık soru", "#fff7ed", "#b45309"));
        return """
                <tr><td class="px" style="padding:0 40px 6px 40px;">
                  <table role="presentation" cellspacing="0" cellpadding="0"><tr>%s</tr></table>
                </td></tr>
                """.formatted(chips.toString());
    }

    private static String countChip(int count, String label, String background, String color) {
        if (count <= 0) {
            return "";
        }
        return """
                <td style="padding:0 8px 0 0;">
                  <div style="background:%s;color:%s;border-radius:999px;padding:6px 13px;font-size:12px;font-weight:700;white-space:nowrap;">%d %s</div>
                </td>
                """.formatted(background, color, count, escape(label));
    }

    private static String draftDecisionsSection(List<String> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return "";
        }
        String rows = decisions.stream()
                .map(text -> """
                        <tr>
                          <td width="20" style="width:20px;vertical-align:top;padding:7px 0 0 0;">
                            <div style="width:7px;height:7px;border-radius:50%%;background:#7c3aed;"></div>
                          </td>
                          <td style="padding:0 0 12px 0;font-size:14px;line-height:1.65;color:#2c3a55;">%s</td>
                        </tr>
                        """.formatted(escape(text)))
                .collect(Collectors.joining());
        return """
                <tr><td class="px" style="padding:18px 40px 0 40px;">
                  <p style="margin:0 0 12px 0;font-size:11px;font-weight:700;color:#6d28d9;text-transform:uppercase;letter-spacing:0.1em;">Kararlar</p>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">%s</table>
                </td></tr>
                """.formatted(rows);
    }

    private static String draftActionsSection(List<DraftMinutesReadyMailBody.ActionLine> actions) {
        if (actions == null || actions.isEmpty()) {
            return "";
        }
        String rows = actions.stream()
                .map(action -> {
                    String meta = actionMeta(action);
                    return """
                            <tr>
                              <td style="padding:13px 16px;border-top:1px solid #eef2f7;font-size:14px;line-height:1.6;color:#2c3a55;">
                                %s%s
                              </td>
                            </tr>
                            """.formatted(escape(action.text()), meta);
                })
                .collect(Collectors.joining());
        return """
                <tr><td class="px" style="padding:18px 40px 0 40px;">
                  <p style="margin:0 0 12px 0;font-size:11px;font-weight:700;color:#0b7a68;text-transform:uppercase;letter-spacing:0.1em;">Aksiyonlar</p>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#ffffff;border:1px solid #e7ebf2;border-radius:12px;">%s</table>
                </td></tr>
                """.formatted(rows);
    }

    private static String actionMeta(DraftMinutesReadyMailBody.ActionLine action) {
        if (!action.hasOwner() && !action.hasDue()) {
            return "";
        }
        StringBuilder meta = new StringBuilder("<div style=\"margin-top:7px;\">");
        if (action.hasOwner()) {
            meta.append("""
                    <span style="display:inline-block;background:#f1f5fb;color:#41506b;border-radius:6px;padding:3px 9px;font-size:12px;font-weight:600;margin-right:6px;">Sorumlu: %s</span>
                    """.formatted(escape(action.owner())));
        }
        if (action.hasDue()) {
            meta.append("""
                    <span style="display:inline-block;background:#fff4ed;color:#b4470a;border-radius:6px;padding:3px 9px;font-size:12px;font-weight:600;">Termin: %s</span>
                    """.formatted(escape(action.dueLabel())));
        }
        return meta.append("</div>").toString();
    }

    private static String draftQuestionsSection(List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return "";
        }
        String rows = questions.stream()
                .map(text -> """
                        <tr>
                          <td width="20" style="width:20px;vertical-align:top;padding:5px 0 0 0;font-size:13px;color:#b45309;font-weight:700;">?</td>
                          <td style="padding:0 0 11px 0;font-size:14px;line-height:1.65;color:#2c3a55;">%s</td>
                        </tr>
                        """.formatted(escape(text)))
                .collect(Collectors.joining());
        return """
                <tr><td class="px" style="padding:18px 40px 0 40px;">
                  <p style="margin:0 0 12px 0;font-size:11px;font-weight:700;color:#b45309;text-transform:uppercase;letter-spacing:0.1em;">Açık sorular</p>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">%s</table>
                </td></tr>
                """.formatted(rows);
    }

    private static String draftReviewNotice(int reviewFlagCount) {
        if (reviewFlagCount <= 0) {
            return "";
        }
        return """
                <tr><td class="px" style="padding:18px 40px 0 40px;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#fffbeb;border:1px solid #fde68a;border-radius:12px;">
                    <tr><td style="padding:14px 18px;font-size:13px;line-height:1.6;color:#92400e;">
                      <strong>%d madde</strong> düşük güven skoru nedeniyle manuel kontrol bekliyor. Onaylamadan önce gözden geçirmeniz önerilir.
                    </td></tr>
                  </table>
                </td></tr>
                """.formatted(reviewFlagCount);
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
                <tr><td class="hero" style="background:#2a1364;background-image:linear-gradient(135deg,#3b1a7a 0%%,#4c1d95 45%%,#0f766e 100%%);padding:34px 40px;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr>
                    <td style="vertical-align:middle;">
                      <table role="presentation" cellspacing="0" cellpadding="0"><tr>
                        <td width="42" style="width:42px;vertical-align:middle;">
                          <div style="width:38px;height:38px;border-radius:11px;background:rgba(255,255,255,0.18);text-align:center;line-height:38px;font-size:17px;color:#ffffff;font-weight:700;">&#9670;</div>
                        </td>
                        <td style="padding-left:13px;vertical-align:middle;">
                          <div style="font-size:11px;font-weight:700;letter-spacing:0.18em;text-transform:uppercase;color:rgba(255,255,255,0.72);">NanobaseAI</div>
                          <div style="font-size:19px;font-weight:700;color:#ffffff;letter-spacing:-0.2px;margin-top:2px;">EasyMeeting</div>
                        </td>
                      </tr></table>
                    </td>
                    <td align="right" style="vertical-align:middle;">
                      <div style="display:inline-block;padding:7px 14px;border-radius:999px;background:rgba(255,255,255,0.15);border:1px solid rgba(255,255,255,0.22);font-size:11px;font-weight:700;color:#ffffff;letter-spacing:0.06em;text-transform:uppercase;">Onay bekliyor</div>
                    </td>
                  </tr></table>
                </td></tr>
                """;
    }

    private static String easyMeetingFooter() {
        return """
                <tr><td style="background:#f7f9fc;padding:22px 40px;border-top:1px solid #e7ebf2;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr>
                    <td align="center">
                      <p style="margin:0 0 5px 0;font-size:12px;font-weight:700;color:#5b6779;letter-spacing:0.03em;">NanobaseAI EasyMeeting</p>
                      <p style="margin:0;font-size:12px;color:#93a0b4;">
                        © Nanobase · <a href="https://www.nanobase.ai" style="color:#6d28d9;text-decoration:none;">www.nanobase.ai</a>
                        &nbsp;·&nbsp; <a href="https://portal.nanobase.ai/easymeeting/" style="color:#6d28d9;text-decoration:none;">Portal</a>
                      </p>
                    </td>
                  </tr></table>
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
