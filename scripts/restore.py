"""Restore to a NEW validation database by default; service replacement needs an explicit flag."""
import argparse,pathlib,re,subprocess
parser=argparse.ArgumentParser();parser.add_argument('backup');parser.add_argument('--database',default='mapmates_restore_check');parser.add_argument('--replace-service-data',action='store_true');args=parser.parse_args()
if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,63}',args.database):parser.error('Invalid database name')
if args.database=='mapmates' and not args.replace_service_data:parser.error('Service replacement requires --replace-service-data; first validate in a new database.')
if args.database!='mapmates' and not args.database.startswith('mapmates_restore_'):parser.error('Validation database must start with mapmates_restore_')
root=['docker','compose','exec','-T','db','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --default-character-set=utf8mb4 "$1"','restore']
if args.database!='mapmates':subprocess.run(root+['mapmates'],input='CREATE DATABASE `'+args.database+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;',text=True,check=True,capture_output=True)
with pathlib.Path(args.backup).open('rb') as data:subprocess.run(root+[args.database],stdin=data,check=True)
print('RESTORED',args.database)
