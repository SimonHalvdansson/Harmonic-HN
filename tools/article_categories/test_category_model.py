import copy
import hashlib
import unittest
from category_model import MODEL_SHA256,fit,labels,normalize,policy,representation,scores
from evaluate_categories import evaluate,validate


def fixture():
    examples=[];records={}
    for index,topic in enumerate(('a','a','b','b'),1):
        sign=1 if topic=='a' else -1
        title=f'Headline {index}';description=f'Useful context for article {index}'
        story=dict(hn_id=index,title=title,description=description,url=f'https://example.com/{index}',category_ids=[topic],split='train')
        preview=normalize([.9,.4*sign,0]+[0]*765);headline=normalize([.9,0,.4*sign]+[0]*765)
        record=dict(hn_id=index,input='preview '+title,title_input='title '+title,input_format=policy()['input_version'],
                    model_sha256=MODEL_SHA256,source_sha256=hashlib.sha256((title+'\0'+description).encode()).hexdigest(),
                    embedding=preview,title_embedding=headline)
        examples.append(story);records[index]=record
    return dict(categories=[dict(id='a'),dict(id='b')],examples=examples),records


class CategoryModelTest(unittest.TestCase):
    def test_each_input_view_has_its_own_background_and_references(self):
        corpus,records=fixture();preview=fit(corpus,records);headline=fit(corpus,records,True)
        for model,title_only in ((preview,False),(headline,True)):
            self.assertEqual(['a'],labels(scores(representation(records[1],title_only),model[0],model[1])))
        self.assertNotEqual(preview[1]['a'],headline[1]['a'])
        self.assertEqual([],labels(scores(representation(records[1],True),preview[0],preview[1])))

    def test_changed_source_text_cannot_silently_reuse_an_old_vector(self):
        corpus,records=fixture();corpus['examples'][0]['title']='A different subject'
        with self.assertRaisesRegex(ValueError,'Stale embedding input'):fit(corpus,records)

    def test_held_out_vectors_do_not_influence_fitting(self):
        corpus,records=fixture();expected=fit(corpus,records)
        held=copy.deepcopy(corpus['examples'][0]);held.update(hn_id=99,url='https://example.com/99',split='evaluation')
        records[99]=copy.deepcopy(records[1]);records[99]['embedding']=records[4]['embedding'];corpus['examples'].append(held)
        self.assertEqual(expected[:2],fit(corpus,records)[:2])

    def test_reports_preserve_the_headline_and_reposts_cannot_cross_splits(self):
        corpus,records=fixture();preview=fit(corpus,records);headline=fit(corpus,records,True)
        result=evaluate(corpus['examples'],records,preview,headline)
        self.assertEqual('Headline 1',result['stories'][0]['title'])
        self.assertIn('title_only',result['stories'][0])
        corpus['examples'][1]['url']='http://www.example.com/1/#repost'
        corpus['examples'][1]['split']='evaluation'
        with self.assertRaisesRegex(ValueError,'Repeated articles'):validate(corpus,{'a','b'})


if __name__=='__main__':unittest.main()
