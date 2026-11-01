"""Evaluate frozen references on development and separately reviewed HN snapshots."""
import argparse
import json
from pathlib import Path
from urllib.parse import urlsplit,urlunsplit
from category_model import fit,has_preview,labels,policy,representation,scores,summarize,validate_records


def article_key(url):
    parts=urlsplit(url)
    return urlunsplit(('',parts.netloc.lower().removeprefix('www.'),parts.path.rstrip('/'),parts.query,''))


def validate(corpus, categories):
    ids=[x['hn_id'] for x in corpus['examples']]
    if len(ids)!=len(set(ids)):raise ValueError('Repeated HN IDs')
    urls=[article_key(x['url']) for x in corpus['examples']]
    if len(urls)!=len(set(urls)):raise ValueError('Repeated articles must be grouped before splitting')
    for x in corpus['examples']:
        if x['split'] not in ('train','evaluation'):raise ValueError('Unknown split')
        accepted=x.get('acceptable_category_ids',x['category_ids'])
        if not set(x['category_ids'])<=set(accepted)<=categories:raise ValueError('Invalid topic labels')


def evaluate(examples, records, preview_model, title_model):
    rows=[]
    for example in examples:
        record=records[example['hn_id']]
        row=dict(hn_id=example['hn_id'],title=example['title'],expected=example['category_ids'],
                 acceptable=example.get('acceptable_category_ids',example['category_ids']),evaluation_set=example.get('evaluation_set','curated'))
        for field in ('preview','title_only'):
            title_only=field=='title_only' or not has_preview(record)
            origin,centers,_=title_model if title_only else preview_model
            values=scores(representation(record,title_only),origin,centers)
            row[field]=dict(scores=values,predicted=labels(values))
        rows.append(row)
    sets=sorted(set(x['evaluation_set'] for x in rows))
    return dict(summary={field:summarize(rows,field) for field in ('preview','title_only')},
                sets={name:{field:summarize([x for x in rows if x['evaluation_set']==name],field) for field in ('preview','title_only')} for name in sets},stories=rows)


def main():
    parser=argparse.ArgumentParser(__doc__)
    parser.add_argument('embeddings',type=Path)
    parser.add_argument('--corpus',type=Path,default=Path(__file__).with_name('corpus.json'))
    parser.add_argument('--validation',type=Path)
    parser.add_argument('--validation-embeddings',type=Path)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    corpus=json.loads(args.corpus.read_text(encoding='utf-8'))
    categories={x['id'] for x in corpus['categories']};validate(corpus,categories)
    records={x['hn_id']:x for x in json.loads(args.embeddings.read_text(encoding='utf-8'))}
    preview=fit(corpus,records);title=fit(corpus,records,title_only=True)
    result=dict(policy=policy(),development=evaluate([x for x in corpus['examples'] if x['split']!='train'],records,preview,title))
    if bool(args.validation)!=bool(args.validation_embeddings):raise ValueError('Pass both validation files')
    if args.validation:
        validation=json.loads(args.validation.read_text(encoding='utf-8'));validate(validation,categories)
        if any(x['split']!='evaluation' for x in validation['examples']):raise ValueError('Validation must never contain training examples')
        known={article_key(x['url']) for x in corpus['examples']}
        if any(article_key(x['url']) in known for x in validation['examples']):raise ValueError('Validation overlaps development articles')
        vectors={x['hn_id']:x for x in json.loads(args.validation_embeddings.read_text(encoding='utf-8'))}
        validate_records(validation,vectors)
        result['validation']=evaluate(validation['examples'],vectors,preview,title)
    args.report.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n')
    for name,data in result.items():
        if name!='policy':print(name,json.dumps(data['summary']))


if __name__=='__main__':main()
