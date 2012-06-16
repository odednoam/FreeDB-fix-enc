#!/usr/bin/env python
#
from __future__ import with_statement
import webapp2
import jinja2
import os
from google.appengine.ext import ndb
from google.appengine.api import users
from google.appengine.ext import blobstore
from google.appengine.ext.webapp import blobstore_handlers
from google.appengine.api import taskqueue
from google.appengine.api import files
import time
import json
import logging
import collections
import re

jinja_environment = jinja2.Environment(
	loader=jinja2.FileSystemLoader(os.path.dirname(__file__)))
serializer = json.JSONEncoder()
deserializer = json.JSONDecoder()

class Language(ndb.Model):
	language = ndb.StringProperty()
	approved = ndb.IntegerProperty(default=0, indexed=False)
	rejected = ndb.IntegerProperty(default=0, indexed=False)
	totalElements = ndb.IntegerProperty(default=0, indexed=False)
	requireFirstPassCoverage = ndb.IntegerProperty(default=0, indexed=False)
	requireSecondPassCoverage = ndb.IntegerProperty(default=0, indexed=False)
	requireThirdPassCoverage = ndb.IntegerProperty(default=0, indexed=False)

class SuspectedEncoding(ndb.Model):
	testString = ndb.TextProperty()
	answeredUsers = ndb.StringProperty(repeated = True)
	approved = ndb.IntegerProperty(default=0, indexed=False)
	rejected = ndb.IntegerProperty(default=0, indexed=False)
	totalAnswered = ndb.IntegerProperty(default=0)
	charset = ndb.StringProperty()


languages = collections.OrderedDict([
			('ar', {'name': 'Arabic', 'dir': 'rtl'}),
			('ar2', {'name': 'Arabic, Persian or Urdu', 'dir': 'rtl'}),
			('cy', {'name': 'Bulgarian, Belarusian, Russian, Serbian or Macedonian (Cyrillic alphabet)'}),
			('cy2',{'name': 'Bulgarian or Russian (Cyrillic alphabet)'}),
			('gb', {'name': 'Chinese (Mainland China)'}),
			('big5',{'name': 'Chinese (Taiwan, Hong Kong, and Macau)'}),
			('gr', {'name': 'Greek'}),
			('he', {'name': 'Hebrew', 'dir': 'rtl'}),
			('jp', {'name': 'Japanese'}),
			('ko', {'name': 'Korean'}),
			('tw', {'name': 'Chinese (Taiwan)'}),
			('lat1',{'name': 'Latin-1'}),
			('tk', {'name': 'Turkish'})])

language_charsets = {
	'ar2': ['ISO-8859-6', 'IBM420_LTR', 'WINDOWS-1256'],
	'cy': ['IBM855', 'ISO-8859-5', 'IBM866', 'MACCYRILLIC', 'WINDOWS-1251'],
	'gb': ['GB18030'],
	'big5': ['BIG5'],
	'lat2': ['ISO-8859-2', 'WINDOWS-1250'],
	'gr': ['ISO-8859-7', 'WINDOWS-1253'],
	'he': ['ISO-8859-8', 'IBM424_LTR', 'IBM424_RTL', 'WINDOWS-1255'],
	'jp': ['SHIFT_JIS', 'EUC-JP'],
	'ko': ['EUC-KR'],
	'cy2': ['KOI8-R'],
	'tw': ['EUC-TW'],
	'tk': ['ISO-8859-9', 'WINDOWS-1254'],
	'lat1': ['ISO-8859-1', 'WINDOWS-1252'],
}
charset_language = {}

for lang in language_charsets:
	for charset in language_charsets[lang]:
		charset_language[charset] = lang;

def getLanguage(lang):
	query = Language.query(Language.language == lang)
	for language in query:
		return language
	language = Language(id = lang)
	language.language = lang
	language.put()
	return language

class UploadFormHandler(webapp2.RequestHandler):
	def get(self):
		template_vars = {}
		user = users.get_current_user()
		if user:
			template_vars['nickname'] = user.nickname()
			template_vars['logout_url'] = users.create_logout_url("/upload")
			if (users.is_current_user_admin()):
				template_vars['admin'] = users.is_current_user_admin()
				template_vars['upload_url'] = blobstore.create_upload_url('/ul')
		else:
			template_vars['login_url'] = users.create_login_url("/upload")

		template = jinja_environment.get_template('upload.html')
		self.response.out.write(template.render(template_vars))
		
class UploadHandler(blobstore_handlers.BlobstoreUploadHandler):
	def post(self):
		upload_files = self.get_uploads('file')  
		encoded = serializer.encode( {
						'key':str(upload_files[0].key()), 
						'filename': upload_files[0].filename, 
						'size': upload_files[0].size, 
						'content_type': upload_files[0].content_type,
						'new_upload_url': blobstore.create_upload_url('/upload')})
		self.response.out.write(encoded)
		
class ParseUploadedHandler(webapp2.RequestHandler):
	def get(self):
		if (users.is_current_user_admin()):
			blob_id = self.request.get('id')
			logging.info("Parsing blob " + blob_id)
			blobKey = blobstore.BlobKey(blob_id)
			blobInfo = blobstore.BlobInfo.get(blobKey)
			reader = blobInfo.open()
			lines = 0
			languages = {}
			start = int(self.request.get('start', 0))
			fetch_length = int(self.request.get('length', 500))
			end = start + fetch_length
			records = []
			line = reader.readline()
			while lines < start:
				lines += 1
				line = reader.readline()
			while lines < end and not line == '': 
				object = deserializer.decode(line)
				if 'language' in object:
					lang = object['language']
				else:
					lang = charset_language[object['charset']]
				if not lang in languages:
					languages[lang] = getLanguage(lang)
				#logging.info("got language: " + languages[lang])
				record = SuspectedEncoding(
					parent = languages[lang].key,
					id = object['file'] + '@' + object['charset'],
					testString = object['testString'],
					charset = object['charset'])
				records.append(record)
				lines += 1
				line = reader.readline()
			
			ndb.put_multi(records)
			if (line == ''):
				self.response.out.write('<html><body>All done.</body></html>')
			else:
				url = '/parse?id=' + self.request.get('id') + '&start=' + str(lines) + '&length=' + str(fetch_length)
				self.response.out.write('<html><body><a href="' + url + '">continue from ' +  str(lines) + '</a><script>window.location = "' + url + '";</script></body></html>')

				
class CountUploadedHandler(webapp2.RequestHandler):
	def get(self):
		if not (users.is_current_user_admin()):
			self.response.status = 403
			self.response.status_message = "Not Authorized"
			return
		langQuery = Language.query()
		for language in langQuery:
			langKey = language.key
			itemsQuery = SuspectedEncoding.query(ancestor=langKey)
			count = itemsQuery.count()
			language.requireFirstPassCoverage  = count
			language.requireSecondPassCoverage  = count
			language.requireThirdPassCoverage  = count
			language.totalElements  = count
			logging.info("got " + str(count) + " records for language " + language.language)
			language.put()

class ValidateHandler(webapp2.RequestHandler):
	def get(self):
		template_vars = {}
		user = users.get_current_user()
		if user:
			lang = self.request.get('lang')
			template_vars['nickname'] = user.nickname()
			template_vars['logout_url'] = users.create_logout_url("/")
			template_vars['admin'] = users.is_current_user_admin()
			template_vars['language'] = languages[lang]['name']
			template_vars['lang'] = lang
			template_vars['dir'] = languages[lang]['dir'] if 'dir' in languages[lang] else 'ltr'
		else:
			template_vars['login_url'] = users.create_login_url("/")
			

		template = jinja_environment.get_template('validate.html')
		self.response.out.write(template.render(template_vars))
		
		

class ValidateQueryHandler(webapp2.RequestHandler):
	def get(self):
		user = users.get_current_user()
		if not user:
			self.response.status = 403
			self.response.status_message = "Not Authorized"
			return
		lang = self.request.get('lang')
		ancestor = getLanguage(lang).key
		#q = SuspectedEncoding.query(SuspectedEncoding.answeredUsers == user, ancestor=ancestor).order(SuspectedEncoding.key)
		userid = user.user_id()
		q = SuspectedEncoding.query(ancestor=ancestor).order(SuspectedEncoding.totalAnswered, SuspectedEncoding.key)
		cursor = self.request.get('cursor')
		if not cursor == '':
			cursor = ndb.query.Cursor(urlsafe=cursor)
		
		fetched = []
		more = True
		while len(fetched) < 10 and more:
			if (cursor == ''):
				logging.info('running query')
				(results, cursor, more) = q.fetch_page(1)
			else:
				logging.info('running query with cursor')
				(results, cursor, more) = q.fetch_page(1, start_cursor=cursor)
			for result in results:
				if not userid in result.answeredUsers:
					fetched.append({'charset': result.charset, 'language': ancestor.id(), 'key': result.key.id(), 'testString': result.testString});
		encoded = serializer.encode( {'resultSet':fetched, 'cursor': cursor.urlsafe(), 'more': more} )
		self.response.out.write(encoded)

class SaveHandler(webapp2.RequestHandler):
	def get(self):
		logging.info("in save handler")
		user = users.get_current_user()
		if not user:
			self.response.status = 403
			self.response.status_message = "Not Authorized"
			return
		for updated in self.request.query_string.split(","):
			if not updated == '':
				(lang, key,value) = updated.split(":");
				logging.info("setting " + lang + "." + key + " = " + value)
				record_key = ndb.Key('Language', lang, 'SuspectedEncoding', key)
				record = record_key.get()
				ancestor_record = record_key.parent().get()
				
				if not user.user_id() in record.answeredUsers:
					record.answeredUsers.append(user.user_id())
					record.totalAnswered +=1
					if (record.totalAnswered == 1):
						ancestor_record.requireFirstPassCoverage -= 1
					elif (record.totalAnswered == 2):
						ancestor_record.requireSecondPassCoverage -= 1
					elif (record.totalAnswered == 3):
						ancestor_record.requireThirdPassCoverage -= 1
						
					if value == 'true':
						record.approved += 1
						ancestor_record.approved += 1
					else:
						record.rejected += 1
						ancestor_record.rejected += 1
					record.put()
					ancestor_record.put()
		self.response.status = 200
		self.response.out.write("done")

class ExtractHandler(webapp2.RequestHandler):
	def get(self):
		if not (users.is_current_user_admin()):
			self.response.status = 403
			self.response.status_message = "Not Authorized"
			return
		taskqueue.add(url='/extractWorker', params={'lang': self.request.get('lang')})
		self.redirect('/')

class ExtractWorker(webapp2.RequestHandler):
	def post(self):
		lang = self.request.get('lang')
		extracted_data = ''
		file_name = files.blobstore.create(mime_type='text/json', _blobinfo_uploaded_filename='extract_' + lang + time.strftime("%y%m%d") + '.json')
		with files.open(file_name, 'a') as f:
			ancestor = ndb.Key('Language', lang)
			for candidate in SuspectedEncoding.query(ancestor=ancestor):
				f.write(serializer.encode({
					'key': candidate.key.id(), 
					'approved': candidate.approved,
					'rejected': candidate.rejected}) + '\n')
		files.finalize(file_name)
		logging.info('finished writing file ' + file_name)

class MainHandler(webapp2.RequestHandler):
	def get(self):
		template_vars = {}
		user = users.get_current_user()
		if user:
			template_vars['nickname'] = user.nickname()
			template_vars['logout_url'] = users.create_logout_url("/")
			template_vars['admin'] = users.is_current_user_admin()
			template_vars['languages'] = collections.OrderedDict([])
			q = Language.query().order(Language.language)
			for lang in q:
				lang_dict = languages[lang.language].copy()
				lang_dict['total'] = lang.totalElements
				lang_dict['first'] =  int(100.0 - 100.0 * lang.requireFirstPassCoverage / lang.totalElements)
				lang_dict['second'] = int(100.0 - 100.0 * lang.requireSecondPassCoverage / lang.totalElements)
				lang_dict['third'] = int(100.0 - 100.0 * lang.requireThirdPassCoverage / lang.totalElements)
				template_vars['languages'][lang.language] = lang_dict
		else:
			template_vars['login_url'] = users.create_login_url("/")

		template = jinja_environment.get_template('index.html')
		self.response.out.write(template.render(template_vars))

app = webapp2.WSGIApplication([('/', MainHandler),
								('/upload', UploadFormHandler),
								('/validate', ValidateHandler),
								('/query', ValidateQueryHandler),
								('/extract', ExtractHandler),
								('/extractWorker', ExtractWorker),
								('/parse', ParseUploadedHandler),
								('/countAdded', CountUploadedHandler),
								('/save', SaveHandler),
								('/ul', UploadHandler)],
							debug=False)
