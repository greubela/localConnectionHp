// Browser replacement for the only `module.createRequire` call used by the
// Deutsche Bahn profile in hafas-client 6. Keeping the JSON import static lets
// webpack include it in the bundle instead of exposing Node's module API.
import dbBaseProfile from "hafas-db-base-profile";

export const createRequire = () => (request) => {
  if (request === "./base.json") return dbBaseProfile;

  throw new Error(`Unsupported browser-side require: ${request}`);
};
