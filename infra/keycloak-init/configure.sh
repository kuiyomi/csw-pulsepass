#!/bin/bash
set -eu
export HOME=/tmp
export KC_CLI_OPTS="-Duser.home=/tmp"
kc=/opt/keycloak/bin/kcadm.sh
kcadm() { local verb=$1; shift; "$kc" "$verb" "$@" --config /tmp/kcadm.config; }
for ((attempt=1; attempt<=60; attempt++)); do
  if kcadm config credentials --server http://keycloak:8080 --realm master --user admin --password "$ADMIN_PASSWORD" >/dev/null 2>&1; then break; fi
  sleep 2
done
realm=pulsepass
client=$(kcadm get clients -r "$realm" -q clientId=iam-governance --fields id --format csv --noquotes)
if [ -z "$client" ]; then
  client=$(kcadm create clients -r "$realm" -s clientId=iam-governance -s publicClient=false -s serviceAccountsEnabled=true -s standardFlowEnabled=false -s directAccessGrantsEnabled=false -s "secret=$IAM_SECRET" -i)
else
  kcadm update "clients/$client" -r "$realm" -s "secret=$IAM_SECRET"
fi
kcadm add-roles -r "$realm" --uusername service-account-iam-governance --cclientid realm-management --rolename manage-users --rolename view-realm
for pair in cliente:CLIENTE organizador:ORGANIZADOR operador:OPERADOR admin-pulse:ADMIN; do
  login=${pair%:*}; role=${pair#*:}
  user=$(kcadm get users -r "$realm" -q "username=$login" -q exact=true --fields id --format csv --noquotes)
  if [ -z "$user" ]; then
    kcadm create users -r "$realm" -s "username=$login" -s enabled=true -s "email=$login@example.test" -s emailVerified=true -s firstName=PulsePass -s "lastName=$role"
    kcadm set-password -r "$realm" --username "$login" --new-password "$DEMO_PASSWORD"
  fi
  kcadm add-roles -r "$realm" --uusername "$login" --rolename "$role"
done
