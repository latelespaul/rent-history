export function delay(ms = 400) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** Randomly throw to simulate server errors (5% by default) */
export async function maybeFail(rate = 0.05) {
  if (Math.random() < rate) {
    throw {
      response: {
        status: 500,
        data: { message: "Simulated server error", status: 500 },
      },
    }
  }
}
