import assert from "node:assert/strict";
import test from "node:test";
import { classifyAttendance } from "./classifyAttendance.ts";

test("classifyAttendance treats JOINED and LEFT as attended", () => {
  assert.equal(classifyAttendance("JOINED", "ENDED"), "attended");
  assert.equal(classifyAttendance("LEFT", "READY"), "attended");
});

test("classifyAttendance treats ABSENT and DECLINED as absent", () => {
  assert.equal(classifyAttendance("ABSENT", "ENDED"), "absent");
  assert.equal(classifyAttendance("DECLINED", "PROCESSING"), "absent");
});

test("classifyAttendance keeps unresolved RSVP pending after meeting finishes", () => {
  assert.equal(classifyAttendance("ACCEPTED", "PROCESSING"), "pending");
  assert.equal(classifyAttendance("INVITED", "ENDED"), "pending");
  assert.equal(classifyAttendance("TENTATIVE", "READY"), "pending");
});
