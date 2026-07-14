#!/usr/bin/env bash
# wild-mtime-probe.sh — verify the "last-visited = .mca chunk timestamp" assumption
# behind the wilderness rolling-reset plan. Runs from YOUR machine (needs `ssh cobblemon`).
#
# It reads the per-chunk timestamp table (header sector 1) of each r.X.Z.mca across
# region/ entities/ poi/ in a coordinate window, after forcing a `save-all flush`, and
# lets you diff two snapshots to see which chunks' timestamps advanced.
#
#   bash ops/wild-mtime-probe.sh snapshot S0     # after Phase 1 (generate)
#   ...gap; Phase 2 (stand)...
#   bash ops/wild-mtime-probe.sh snapshot S1     # after Phase 2
#   bash ops/wild-mtime-probe.sh diff S0 S1      # paste this output back
#
# Env overrides: REMOTE, WORLD, SCREEN, XMIN/XMAX/ZMIN/ZMAX (region coords).
set -euo pipefail

REMOTE=${REMOTE:-cobblemon}
WORLD=${WORLD:-/opt/cobblemon-dev/world}
SCREEN=${SCREEN:-cobblemon-dev}
XMIN=${XMIN:-40}; XMAX=${XMAX:-46}; ZMIN=${ZMIN:--4}; ZMAX=${ZMAX:-4}
DIR=/tmp/wild-probe; mkdir -p "$DIR"

# Remote decoder: emits  folder<TAB>rx<TAB>rz<TAB>present<TAB>max_ts  for present chunks.
read -r -d '' PY <<'PY' || true
import os, glob, struct, time
w=os.environ['PWORLD']
xmin=int(os.environ['PXMIN']); xmax=int(os.environ['PXMAX'])
zmin=int(os.environ['PZMIN']); zmax=int(os.environ['PZMAX'])
print("NOW\t%d" % int(time.time()))
for folder in ('region','entities','poi'):
    for rx in range(xmin, xmax+1):
        for rz in range(zmin, zmax+1):
            f=os.path.join(w, folder, "r.%d.%d.mca" % (rx,rz))
            try:
                with open(f,'rb') as fh: head=fh.read(8192)
            except OSError:
                continue
            if len(head) < 8192: continue
            loc=head[:4096]; ts=head[4096:8192]; mx=0; pres=0
            for i in range(1024):
                if int.from_bytes(loc[i*4:i*4+3],'big')==0: continue
                pres+=1
                t=struct.unpack('>I', ts[i*4:i*4+4])[0]
                if t>mx: mx=t
            if pres:
                print("%s\t%d\t%d\t%d\t%d" % (folder,rx,rz,pres,mx))
PY

snapshot() {
  local label=${1:?usage: snapshot <label>}
  echo ">> forcing save-all flush on $REMOTE ($SCREEN) ..."
  ssh "$REMOTE" "screen -S '$SCREEN' -p 0 -X stuff 'save-all flush\r'" || echo "   (screen send failed; continuing with on-disk state)"
  sleep 7
  echo ">> reading timestamps (regions x[$XMIN..$XMAX] z[$ZMIN..$ZMAX]) ..."
  ssh "$REMOTE" "PWORLD='$WORLD' PXMIN=$XMIN PXMAX=$XMAX PZMIN=$ZMIN PZMAX=$ZMAX python3 -" <<<"$PY" > "$DIR/$label.tsv"
  echo ">> saved $DIR/$label.tsv"
  awk -F'\t' 'NR==1{next} {printf "   %-8s r.%s.%s  present=%s  age=%.1fh\n",$1,$2,$3,$4,(now-$5)/3600}' now="$(date +%s)" "$DIR/$label.tsv" | head -80
}

diffs() {
  local a=${1:?} b=${2:?}
  python3 - "$DIR/$a.tsv" "$DIR/$b.tsv" <<'PY'
import sys
def load(p):
    d={}
    for ln in open(p):
        ln=ln.rstrip('\n')
        if not ln: continue
        if ln.startswith('NOW'): continue
        fo,rx,rz,pres,mx=ln.split('\t')
        d[(fo,int(rx),int(rz))]=(int(pres),int(mx))
    return d
A=load(sys.argv[1]); B=load(sys.argv[2])
keys=sorted(set(list(A)+list(B)))
print("%-8s %4s %4s  %10s %10s  %8s  %s" % ("folder","rx","rz","A_ts","B_ts","d_sec","status"))
for k in keys:
    a=A.get(k); b=B.get(k)
    amx=a[1] if a else 0; bmx=b[1] if b else 0
    d=bmx-amx
    if not a: st="NEW"
    elif not b: st="gone"
    elif d>0: st="ADVANCED +%.1fmin"%(d/60)
    else: st="frozen"
    print("%-8s %4d %4d  %10d %10d  %8d  %s" % (k[0],k[1],k[2],amx,bmx,d,st))
PY
}

case "${1:-}" in
  snapshot) shift; snapshot "$@";;
  diff)     shift; diffs "$@";;
  *) echo "usage: $0 snapshot <label> | diff <labelA> <labelB>"; exit 1;;
esac
