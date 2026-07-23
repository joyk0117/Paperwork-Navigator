FROM mcr.microsoft.com/devcontainers/python:3.12-bookworm

# WikiCommitのPythonスクリプト（.wikicommit/scripts/）が使うパッケージ
RUN pip install --no-cache-dir pyyaml python-frontmatter requests markitdown

# lychee（外部リンク検証。wikicommit-init の自動インストール（cargo build）に頼らず事前に入れておく）
ARG LYCHEE_VERSION=0.15.0
RUN curl -fsSL \
  "https://github.com/lycheeverse/lychee/releases/download/v${LYCHEE_VERSION}/lychee-v${LYCHEE_VERSION}-x86_64-unknown-linux-gnu.tar.gz" \
  | tar xz -C /usr/local/bin lychee