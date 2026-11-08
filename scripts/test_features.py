"""Feature integration tests. Creates disposable accounts and cleans them on completion."""
import unittest,time,datetime,concurrent.futures
from test_api import Client
class Features(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.clients=[Client() for _ in range(3)];cls.users=[];cls.profiles=[]
  for i,c in enumerate(cls.clients):
   p=dict(email=f'feature-{time.time_ns()}@mapmates.test',password='travel1234',name='기능검증',nickname=f'기능{i}{time.time_ns()}'[-20:],birthDate='1998-01-01',gender='female')
   code,u=c.call('members','POST',p);assert code==201,(code,u);cls.users.append(p);cls.profiles.append(u)
  cls.a,cls.b,cls.c=cls.clients
  cls.place=cls.a.call('attractions?areaCode=1&size=1')[1]['items'][0]
 @classmethod
 def tearDownClass(cls):
  for c in cls.clients:c.call('members/me','DELETE',{'currentPassword':'travel1234'})
 def trip(self,policy='instant'):
  day=str(datetime.date.today()+datetime.timedelta(days=10))
  body=dict(title='기능 검증 여행',startDate=day,endDate=day,capacity=2,budget=0,region='서울',theme='문화',description='테스트',items=[dict(day=1,time='10:00',memo='',place=self.place)])
  code,p=self.a.call('plans','POST',body);self.assertEqual(code,201,p)
  code,c=self.a.call('companions','POST',{'planId':int(p['id'])});self.assertEqual(code,201,c)
  self.assertEqual(self.a.call(f'companions/{c["id"]}','PUT',{'joinPolicy':policy})[0],200)
  body['version']=p['trip']['version']
  return p,c,body
 def test_01_approval(self):
  p,cp,_=self.trip('approval');cid=cp['id']
  try:
   self.assertEqual(self.b.call(f'companions/{cid}/participants','POST',{})[0],400)
   for c in [self.b,self.c]:self.assertEqual(c.call(f'companions/{cid}/participants','POST',{'introduction':'함께 걷고 싶어요'})[0],200)
   self.assertEqual(len(self.a.call(f'companions/{cid}')[1]['participants']),1)
   self.assertEqual(self.b.call(f'companions/{cid}/requests')[0],403)
   self.assertEqual(self.b.call(f'companions/{cid}/requests/me')[1]['status'],'pending')
   def approve(i):
    # Independent HTTP clients using the host session for simultaneous approvals.
    return self.a.call(f'companions/{cid}/requests/{self.profiles[i]["id"]}','PUT',{'status':'approved'})
   with concurrent.futures.ThreadPoolExecutor() as pool:codes=list(pool.map(approve,[1,2]))
   self.assertEqual(sorted(r[0] for r in codes),[204,409],codes)
   self.assertEqual(len(self.a.call(f'companions/{cid}')[1]['participants']),2)
   for c in [self.b,self.c]:self.assertEqual(c.call(f'companions/{cid}/participants/me','DELETE')[0],200)
   self.assertEqual(self.b.call(f'companions/{cid}/requests/me')[1]['status'],'cancelled')
  finally:self.a.call('plans/'+p['id'],'DELETE')
 def test_02_notifications(self):
  p,cp,_=self.trip('approval');cid=cp['id']
  try:
   self.b.call(f'companions/{cid}/participants','POST',{'introduction':'신청합니다'})
   data=self.a.call('notifications')[1];self.assertGreater(data['unread'],0)
   n=next(x for x in data['items'] if x['link']==f'companion-detail.html?id={cid}')
   self.assertEqual(self.c.call(f'notifications/{n["id"]}/read','PUT',{})[0],404)
   self.assertEqual(self.a.call(f'notifications/{n["id"]}/read','PUT',{})[0],204)
   self.a.call(f'companions/{cid}/requests/{self.profiles[1]["id"]}','PUT',{'status':'approved'})
   self.assertTrue(any(x['kind']=='request_decided' for x in self.b.call('notifications')[1]['items']))
   self.assertEqual(self.b.call('notifications/read-all','PUT',{})[0],204)
   self.assertEqual(self.b.call('notifications')[1]['unread'],0)
  finally:self.a.call('plans/'+p['id'],'DELETE')
 def test_03_lifecycle_and_revisions(self):
  p,cp,body=self.trip();cid=cp['id']
  try:
   self.b.call(f'companions/{cid}/participants','POST',{})
   self.assertEqual(self.a.call(f'companions/{cid}/lifecycle','PUT',{'lifecycle':'completed'})[0],409)
   self.assertEqual(self.b.call(f'companions/{cid}/lifecycle','PUT',{'lifecycle':'cancelled'})[0],403)
   body['title']='바뀐 일정';self.assertEqual(self.a.call('plans/'+p['id'],'PUT',body)[0],200)
   self.assertEqual(len(self.b.call('plans/'+p['id']+'/revisions')[1]),1)
   self.assertEqual(self.c.call('plans/'+p['id']+'/revisions')[0],403)
   self.assertTrue(any(n['kind']=='plan_changed' for n in self.b.call('notifications')[1]['items']))
   self.assertEqual(self.a.call(f'companions/{cid}/lifecycle','PUT',{'lifecycle':'cancelled'})[0],200)
   self.assertEqual(self.c.call(f'companions/{cid}/participants','POST',{})[0],409)
   self.assertEqual(self.a.call('plans/'+p['id'],'PUT',body)[0],409)
  finally:self.a.call('plans/'+p['id'],'DELETE')
 def test_04_plan_version_conflict(self):
  p,cp,body=self.trip()
  try:
   updated={**body,'title':'먼저 저장'}
   code,result=self.a.call('plans/'+p['id'],'PUT',updated);self.assertEqual(code,200,result)
   self.assertEqual(result['trip']['version'],body['version']+1)
   self.assertEqual(self.a.call('plans/'+p['id'],'PUT',body)[0],409)
   self.assertEqual(self.a.call('plans/'+p['id'])[1]['trip']['title'],'먼저 저장')
  finally:self.a.call('plans/'+p['id'],'DELETE')
 def test_05_pagination(self):
  ids=[]
  try:
   for n in range(3):
    code,p=self.a.call('posts','POST',dict(board='travel',category='검증',title=f'페이징검증{n}',content='검색과 페이지 검증'));self.assertEqual(code,201,p);ids.append(p['id'])
   one=self.a.call('posts?keyword=페이징검증&size=2&page=1')[1];two=self.a.call('posts?keyword=페이징검증&size=2&page=2')[1]
   self.assertEqual(one['total'],3);self.assertEqual(len(one['items']),2);self.assertEqual(len(two['items']),1)
   self.assertFalse(set(p['id'] for p in one['items'])&set(p['id'] for p in two['items']))
   cp=self.a.call('companions?size=1')[1];self.assertLessEqual(len(cp['items']),1)
   if cp['items']:
    detail=self.a.call('companions/'+str(cp['items'][0]['id']))[1];self.assertEqual(detail['people'],cp['items'][0]['people']);self.assertEqual(len(detail['schedule']),len(cp['items'][0]['schedule']))
  finally:
   for pid in ids:self.a.call('posts/'+str(pid),'DELETE')
if __name__=='__main__':unittest.main(verbosity=2)
