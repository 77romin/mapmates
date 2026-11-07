"""HTTP integration scenarios against a running MapMates/MySQL instance; cleans own accounts."""
import concurrent.futures, http.cookiejar, json, os, time, unittest, urllib.error, urllib.request
BASE=os.environ.get('TEST_URL','http://localhost:8080/mapmates')+'/api/'
class Client:
 def __init__(self): self.jar=http.cookiejar.CookieJar();self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
 def call(self,path,method='GET',body=None,headers=None):
  req=urllib.request.Request(BASE+path,method=method,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json',**(headers or {})})
  try:
   with self.opener.open(req,timeout=20) as r:return r.status,json.loads(r.read() or b'null')
  except urllib.error.HTTPError as e:return e.code,json.loads(e.read() or b'null')
class Integration(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.a=Client();cls.b=Client();cls.c=Client();cls.anon=Client();cls.users=[]
  for i,client in enumerate([cls.a,cls.b,cls.c]):
   payload=dict(email=f'test-{time.time_ns()}-{i}@mapmates.test',password='travel1234',name='검증회원',nickname=f'검증{time.time_ns()}'[-20:],birthDate='1998-01-01',gender='female')
   code,user=client.call('members','POST',payload);assert code==201,(code,user);cls.users.append(payload)
  code,places=cls.a.call('attractions?areaCode=1&size=5');assert code==200,places
  cls.place=next(p for p in places['items'] if p['lat'] and p['lng'])
  cls.plan=dict(title='통합 검증 여행',startDate='2026-11-14',endDate='2026-11-14',capacity=2,budget=30000,region='서울',theme='문화',description='설명',items=[dict(day=1,time='10:00',memo='메모',place=cls.place)])
 @classmethod
 def tearDownClass(cls):
  for c in [cls.a,cls.b,cls.c]:c.call('members/me','DELETE',{'currentPassword':'travel1234'})
 def test_01_auth(self):
  self.assertEqual(self.anon.call('plans')[0],401)
  self.assertEqual(self.anon.call('members','POST',self.users[0])[0],409)
  self.assertNotIn('password',json.dumps(self.a.call('auth/me')[1]))
  self.assertEqual(self.a.call('posts','POST',dict(board='notice',category='안내',title='제목',content='관리자 전용'))[0],403)
  self.assertEqual(self.a.call('auth/logout','POST',{}, {'Origin':'https://evil.example'})[0],403)
 def test_02_catalog(self):
  self.assertGreater(self.a.call('attractions?areaCode=1')[1]['total'],0)
  self.assertEqual(self.a.call('attractions/nearby?lat=NaN')[0],400)
  self.assertEqual(self.a.call('areas/1/guguns')[0],200)
  self.assertEqual(self.a.call('content-types')[0],200)
 def test_03_plan_transaction_and_permission(self):
  code,p=self.a.call('plans','POST',self.plan);self.assertEqual(code,201,p);pid=p['id']
  self.assertEqual(p['trip']['title'],self.plan['title'])
  self.assertEqual(self.b.call('plans/'+pid)[0],403)
  self.assertEqual(self.b.call('plans/'+pid,'DELETE')[0],403)
  invalid={**self.plan,'items':[dict(day=2,time='10:00',place=self.place)]}
  self.assertEqual(self.a.call('plans/'+pid,'PUT',invalid)[0],400)
  self.assertEqual(len(self.a.call('plans/'+pid)[1]['tripSchedule']),1)
  self.assertEqual(self.a.call('plans/'+pid,'DELETE')[0],204)
 def test_04_concurrent_join_and_closure(self):
  _,p=self.a.call('plans','POST',self.plan);_,cp=self.a.call('companions','POST',dict(planId=int(p['id'])));cid=cp['id']
  with concurrent.futures.ThreadPoolExecutor() as pool:results=list(pool.map(lambda c:c.call(f'companions/{cid}/participants','POST',{}),[self.b,self.c]))
  self.assertEqual(sorted(r[0] for r in results),[200,409],results)
  current=self.a.call(f'companions/{cid}')[1];self.assertEqual(len(current['participants']),2)
  winner=self.b if results[0][0]==200 else self.c
  self.assertEqual(winner.call('plans/'+p['id'])[0],200)
  self.assertEqual(self.a.call('plans/'+p['id'],'PUT',self.plan)[0],200)
  self.assertEqual(self.a.call(f'companions/{cid}/close','POST',{})[0],200)
  self.assertEqual(winner.call(f'companions/{cid}/participants/me','DELETE')[0],200)
  self.assertEqual(winner.call(f'companions/{cid}/participants','POST',{})[0],409)
  self.assertEqual(self.a.call(f'companions/{cid}','PUT',dict(closed=False))[0],200)
  self.assertEqual(winner.call(f'companions/{cid}/participants','POST',{})[0],200)
  self.assertEqual(self.a.call('plans/'+p['id'],'DELETE')[0],204)
 def test_05_posts_and_comments(self):
  payload=dict(board='travel',category='후기',title='여행 검증',content='여행 정보를 공유합니다.')
  code,p=self.a.call('posts','POST',payload);self.assertEqual(code,201,p);pid=p['id']
  viewed=self.b.call(f'posts/{pid}')[1];self.assertEqual(viewed['views'],1);self.assertIsNone(viewed['updatedAt'])
  self.assertEqual(self.b.call(f'posts/{pid}','PUT',payload)[0],403)
  code,comments=self.b.call(f'posts/{pid}/comments','POST',dict(content='댓글'));self.assertEqual(code,201,comments);root=comments[0]['id']
  code,comments=self.c.call(f'posts/{pid}/comments','POST',dict(content='답글',parentId=int(root)));self.assertEqual(code,201,comments)
  _,other=self.a.call('posts','POST',payload)
  self.assertEqual(self.c.call(f'posts/{other["id"]}/comments','POST',dict(content='다른 글 답글',parentId=int(root)))[0],400)
  self.assertEqual(self.a.call(f'comments/{root}','DELETE')[0],204)
  comments=self.a.call(f'posts/{pid}/comments')[1];self.assertTrue(comments[0]['deleted']);self.assertEqual(comments[0]['content'],'');self.assertEqual(len(comments),2)
  self.assertEqual(self.a.call(f'posts/{pid}','PUT',payload)[0],200)
  self.assertIsNotNone(self.a.call(f'posts/{pid}')[1]['updatedAt'])
  self.assertEqual(self.a.call(f'posts/{pid}','DELETE')[0],204)
  self.assertEqual(self.a.call(f'posts/{pid}/comments')[1],[])
  self.a.call(f'posts/{other["id"]}','DELETE')
 def test_06_favorites_and_popularity(self):
  for _ in range(2):self.assertEqual(self.a.call('favorites','POST',self.place)[0],201)
  self.assertEqual(len(self.a.call('favorites')[1]),1)
  self.assertEqual(self.a.call(f'favorites/{self.place["id"]}','DELETE')[0],204)
  code,items=self.a.call('popular-places?south=33&north=39&west=124&east=132');self.assertEqual(code,200,items);self.assertTrue(items)
 def test_07_password_and_logout(self):
  self.assertEqual(self.c.call('members/me/password','PUT',dict(currentPassword='wrong',newPassword='next12345'))[0],400)
  self.assertEqual(self.c.call('auth/logout','POST',{})[0],204)
  self.assertEqual(self.c.call('plans')[0],401)
  self.assertEqual(self.c.call('auth/login','POST',dict(email=self.users[2]['email'],password='wrong'))[0],401)
  self.assertEqual(self.c.call('auth/login','POST',dict(email=self.users[2]['email'],password='travel1234'))[0],200)
if __name__=='__main__':unittest.main(verbosity=2)
