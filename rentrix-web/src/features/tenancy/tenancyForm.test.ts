import { describe, expect, it } from "vitest"
import { checkTenancyForm, friendlyFieldError } from "./tenancyForm"

const EMPTY = { asLandlord: true, email: "", startDate: "", endDate: "", rent: "", deposit: "" }

describe("checkTenancyForm", () => {
  it("names every missing required field, the rent included", () => {
    expect(checkTenancyForm(EMPTY)).toEqual({
      counterpartyEmail: "Enter the tenant's email address.",
      startDate: "Enter the start date.",
      initialRentInr: "Enter the monthly rent.",
    })
  })

  it("asks a tenant for no email", () => {
    expect(checkTenancyForm({ ...EMPTY, asLandlord: false }).counterpartyEmail).toBeUndefined()
  })

  it("passes a complete form", () => {
    expect(
      checkTenancyForm({
        ...EMPTY,
        email: "a@example.test",
        startDate: "2026-01-01",
        rent: "25,000",
        deposit: "",
      }),
    ).toEqual({})
  })

  it("explains a rent, deposit or end date it cannot use", () => {
    const errors = checkTenancyForm({
      ...EMPTY,
      email: "a@example.test",
      startDate: "2026-05-01",
      endDate: "2026-04-30",
      rent: "twenty thousand",
      deposit: "-5",
    })
    expect(errors.initialRentInr).toMatch(/whole number of rupees/)
    expect(errors.depositInr).toMatch(/whole number of rupees/)
    expect(errors.endDate).toBe("The end date must be on or after the start date.")
    expect(checkTenancyForm({ ...EMPTY, rent: "0" }).initialRentInr).toBe(
      "Enter a monthly rent above ₹0.",
    )
  })
})

describe("friendlyFieldError", () => {
  it("replaces raw bean-validation messages with the form's words", () => {
    expect(
      friendlyFieldError({ field: "startDate", code: "not_null", message: "must not be null" }),
    ).toBe("Enter the start date.")
    expect(
      friendlyFieldError({
        field: "counterpartyEmail",
        code: "email",
        message: "must be a well-formed email address",
      }),
    ).toBe("Enter a valid email address, like name@example.com.")
    expect(
      friendlyFieldError({
        field: "initialRentInr",
        code: "max",
        message: "must be less than or equal to 10000000",
      }),
    ).toBe("That rent looks too high: check the number.")
    expect(
      friendlyFieldError({
        field: "depositInr",
        code: "max",
        message: "must be less than or equal to 100000000",
      }),
    ).toBe("That deposit looks too high: check the number.")
    expect(
      friendlyFieldError({ field: "initialRentInr", code: "min", message: "must be at least 1" }),
    ).toMatch(/whole number of rupees/)
  })

  it("keeps a message the API wrote for people", () => {
    const message = "Only the landlord names the other party."
    expect(friendlyFieldError({ field: "counterpartyEmail", code: "not_allowed", message })).toBe(
      message,
    )
    expect(friendlyFieldError({ field: "somethingElse", code: "not_null", message: "x" })).toBe("x")
  })
})
