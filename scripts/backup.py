"""Consistent InnoDB backup; private file permissions; never overwrites an existing file."""
import argparse,datetime,os,pathlib,subprocess,hashlib
parser=argparse.ArgumentParser();parser.add_argument('--output');args=parser.parse_args()
target=pathlib.Path(args.output or '.backups/mapmates-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.sql');target.parent.mkdir(parents=True,exist_ok=True)
command=['docker','compose','exec','-T','db','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --events --triggers mapmates']
fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
try:
 with os.fdopen(fd,'wb') as output:subprocess.run(command,stdout=output,stderr=subprocess.PIPE,check=True)
except Exception:
 target.unlink();raise
h=hashlib.sha256()
with target.open('rb') as source:
 for chunk in iter(lambda:source.read(1024*1024),b''):h.update(chunk)
print('BACKUP',target,'SHA256',h.hexdigest())
