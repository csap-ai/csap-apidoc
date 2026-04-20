# Publishing to Maven Central

This project publishes to **Maven Central** via the **[Sonatype Central
Portal]** (Sonatype's replacement for the legacy OSSRH / `oss.sonatype.org`
pipeline, which was sunset in April 2025).

[Sonatype Central Portal]: https://central.sonatype.com/

Once the one-time setup below is complete, each release is a single
GitHub Actions workflow run: `Release to Maven Central` →
`Run workflow` → type the version → Enter.

---

## TL;DR — release a version

1. Cut a release PR that bumps `<version>` in the parent `pom.xml` (and
   whatever `CHANGELOG.md` / `README.md` references), merge to `main`.
2. Go to **Actions → Release to Maven Central → Run workflow**.
3. Type the version (e.g. `1.0.0`), leave `dry_run=false`, click **Run**.
4. 5–15 minutes later the bundle is live on
   [central.sonatype.com](https://central.sonatype.com/artifact/ai.csap.apidoc/csap-apidoc)
   and (within ~30 min, sometimes longer) on
   [search.maven.org](https://search.maven.org/). A `vX.Y.Z` git tag is
   pushed automatically.

If anything goes wrong, set `dry_run=true` and re-run — that builds,
signs, and runs the full `release` profile locally on the runner
**without** uploading anything.

---

## One-time setup

You only need to do this once per repository. All three pieces live in
**repo Settings → Secrets and variables → Actions → New repository
secret**.

### 1. Central Portal User Token

1. Log in at <https://central.sonatype.com/> with the account that owns
   the `ai.csap` namespace.
2. Top-right avatar → **View Account** → **Generate User Token**.
3. You'll get a username + password pair. Save them as:
   * `CENTRAL_USERNAME`
   * `CENTRAL_PASSWORD`

These are **not** your portal login — they are per-publisher tokens and
can be rotated / revoked independently.

### 2. GPG signing key

Maven Central requires **every** artifact (`.jar`, `.pom`, `-sources.jar`,
`-javadoc.jar`) to have a detached `.asc` signature.

```bash
# Generate a new RSA 4096 key dedicated to releases.
gpg --batch --gen-key <<'EOF'
Key-Type: RSA
Key-Length: 4096
Subkey-Type: RSA
Subkey-Length: 4096
Name-Real: CSAP Release Bot
Name-Email: release@csap.ai
Expire-Date: 2y
Passphrase: <PICK-A-LONG-PASSPHRASE>
%commit
EOF

# Find the key id (the long hex string on the "pub" line).
gpg --list-secret-keys --keyid-format long release@csap.ai

# Upload the public key to the three keyservers Central checks. Do ALL
# THREE; Central rotates which one it queries.
KEYID=<the-key-id-from-above>
gpg --keyserver keys.openpgp.org     --send-keys "$KEYID"
gpg --keyserver keyserver.ubuntu.com --send-keys "$KEYID"
gpg --keyserver pgp.mit.edu          --send-keys "$KEYID"

# Export the ARMORED PRIVATE key — this is what goes into the
# GitHub secret (setup-java will import it into the runner's keyring).
gpg --armor --export-secret-keys "$KEYID" > gpg-private-key.asc
```

Then create two more secrets:

* `GPG_PRIVATE_KEY` — paste the entire contents of `gpg-private-key.asc`
  including the `-----BEGIN PGP PRIVATE KEY BLOCK-----` / `-----END
  PGP PRIVATE KEY BLOCK-----` lines
* `GPG_PASSPHRASE` — the passphrase you chose above

**Delete `gpg-private-key.asc` from disk once the secret is saved** —
it's the crown jewels and can be re-exported any time from your keyring.

### 3. Namespace verification

Already done — `ai.csap` shows as **Verified** under
<https://central.sonatype.com/> → Publishing Settings → Namespaces.
No action needed unless you add a new top-level namespace.

---

## What `-P release` actually does

Activating the `release` profile in `pom.xml` turns on four plugins on top
of the default build:

| Plugin | What it produces | Required by Central? |
|---|---|---|
| `maven-source-plugin` | `<module>-<version>-sources.jar` | yes |
| `maven-javadoc-plugin` | `<module>-<version>-javadoc.jar` | yes |
| `maven-gpg-plugin` | `.asc` signature for every artifact | yes |
| `central-publishing-maven-plugin` | Bundles the reactor + POSTs to the Portal; polls until the Portal confirms publication | yes |

The plugin is configured with `<autoPublish>true</autoPublish>` — so no
manual "Publish" click on the Portal UI is required. If a validation
fails (e.g. missing javadoc jar, bad signature, SHA mismatch) the whole
`mvn deploy` fails and nothing is released.

`<waitUntil>published</waitUntil>` makes the workflow block until the
Portal actually accepts the bundle, so a green CI run means the artifact
is genuinely on Central.

---

## Running a manual dry-run locally

Useful before the first real release, or if you want to poke at the
signed artifacts before pushing.

```bash
# settings.xml: put your Central token under <server id="central">
# gpg: keyring already has your key

mvn -P release -DskipTests clean verify \
    -Dgpg.passphrase=<your-passphrase>

# Inspect the bundle that would be uploaded:
ls -lh target/central-publishing/
```

`verify` runs source + javadoc + gpg-sign but **stops before** the
`deploy` phase, so nothing goes over the network.

---

## Troubleshooting

### `401 Unauthorized` from the Portal

The `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` secrets are wrong, or you
generated a User Token under an account that doesn't own `ai.csap`.
Regenerate at <https://central.sonatype.com/account>.

### `Bundle validation failed: PGP signature invalid`

Your public key isn't on the keyserver Central is currently checking, or
it was uploaded less than ~10 min ago and hasn't propagated. Run the
three `gpg --send-keys` commands again, wait 10 min, and re-trigger the
workflow.

### `gpg: signing failed: No such file or directory`

`GPG_PRIVATE_KEY` was saved without the armored header / footer, or
newlines got mangled. Re-export with `gpg --armor --export-secret-keys`
and paste the **entire** block including the BEGIN / END lines.

### Version already published

Maven Central is immutable — once a version lands, you cannot overwrite
or delete it. Bump to the next patch version and release again.

### Javadoc errors

The `release` profile sets `failOnError=false` and `-Xdoclint:none`, so
you'd have to have genuinely broken syntax to trip this. If it does
happen, fix the offending Javadoc comment and re-run.

---

## Release checklist (copy into the PR description)

```
- [ ] `pom.xml` version bumped to X.Y.Z (no -SNAPSHOT)
- [ ] `CHANGELOG.md` has an `## [X.Y.Z] - YYYY-MM-DD` section
- [ ] `README.md` / `README.en.md` / `docs/guides/QUICK_START.md` / `docs/index.md` reference X.Y.Z
- [ ] CI green (ci.yml + docs.yml)
- [ ] Dry-run of Release workflow succeeded
- [ ] Tagged v X.Y.Z (workflow does this automatically on success)
```
